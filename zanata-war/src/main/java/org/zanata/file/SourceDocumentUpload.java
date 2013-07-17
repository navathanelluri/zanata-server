/*
 * Copyright 2013, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.Blob;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.LobHelper;
import org.hibernate.Session;
import org.jboss.seam.security.AuthorizationException;
import org.zanata.common.DocumentType;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.exception.ChunkUploadException;
import org.zanata.exception.VirusDetectedException;
import org.zanata.exception.ZanataServiceException;
import org.zanata.model.HDocument;
import org.zanata.model.HDocumentUpload;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HRawDocument;
import org.zanata.rest.DocumentFileUploadForm;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.ChunkUploadResponse;
import org.zanata.rest.dto.extensions.ExtensionType;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.service.VirusScanner;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.DocumentService;
import org.zanata.service.TranslationFileService;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

@Slf4j
public class SourceDocumentUpload extends DocumentUpload
{

   private static final HLocale NULL_LOCALE = null;

   public SourceDocumentUpload(ZanataIdentity identity,
         Session session,
         DocumentDAO documentDAO,
         ProjectIterationDAO projectIterationDAO,
         DocumentService documentServiceImpl,
         VirusScanner virusScanner,
         TranslationFileService translationFileServiceImpl)
   {
      super(identity,
            session,
            documentDAO,
            projectIterationDAO,
            documentServiceImpl,
            virusScanner,
            translationFileServiceImpl);
   }

   public Response tryUploadSourceFile(GlobalDocumentId id, DocumentFileUploadForm uploadForm)
   {
      try
      {
         checkSourceUploadPreconditions(id, uploadForm);

         Optional<File> tempFile;
         int totalChunks;

         if (!uploadForm.getLast())
         {
            HDocumentUpload upload = saveUploadPart(id, NULL_LOCALE, uploadForm, session, projectIterationDAO);
            totalChunks = upload.getParts().size();
            return Response.status(Status.ACCEPTED)
                  .entity(new ChunkUploadResponse(upload.getId(), totalChunks, true,
                        "Chunk accepted, awaiting remaining chunks."))
                  .build();
         }

         if (isSinglePart(uploadForm))
         {
            totalChunks = 1;
            tempFile = Optional.<File> absent();
         }
         else
         {
            HDocumentUpload upload = saveUploadPart(id, NULL_LOCALE,
                  uploadForm, session, projectIterationDAO);
            totalChunks = upload.getParts().size();
            tempFile = Optional.of(combineToTempFileAndDeleteUploadRecord(upload, session, translationFileServiceImpl));
         }

         if (uploadForm.getFileType().equals(".pot"))
         {
            InputStream potStream = getInputStream(tempFile, uploadForm);
            parsePotFile(potStream, id, uploadForm);
         }
         else
         {
            if (!tempFile.isPresent())
            {
               tempFile = Optional.of(persistTempFileFromUpload(uploadForm, translationFileServiceImpl));
            }
            processAdapterFile(tempFile.get(), id, uploadForm);
         }
         if (tempFile.isPresent())
         {
            tempFile.get().delete();
         }
         return sourceUploadSuccessResponse(isNewDocument(id, documentDAO), totalChunks);
      }
      catch (ChunkUploadException e)
      {
         return Response.status(e.getStatusCode())
               .entity(new ChunkUploadResponse(e.getMessage()))
               .build();
      }
      catch (FileNotFoundException e)
      {
         log.error("failed to create input stream from temp file", e);
         return Response.status(Status.INTERNAL_SERVER_ERROR)
               .entity(e).build();
      }
   }

   private void checkSourceUploadPreconditions(GlobalDocumentId id,
         DocumentFileUploadForm uploadForm)
   {
      try
      {
         checkUploadPreconditions(id, uploadForm, identity, projectIterationDAO, session);
         checkSourceUploadAllowed(id);
      }
      catch (AuthorizationException e)
      {
         throw new ChunkUploadException(Status.UNAUTHORIZED, e.getMessage());
      }
      checkValidSourceUploadType(uploadForm);
   }

   private void checkSourceUploadAllowed(GlobalDocumentId id)
   {
      if (!isDocumentUploadAllowed(id))
      {
         throw new ChunkUploadException(Status.FORBIDDEN,
               "You do not have permission to upload source documents to project-version \""
                     + id.getProjectSlug() + ":" + id.getVersionSlug() + "\".");
      }
   }

   private boolean isDocumentUploadAllowed(GlobalDocumentId id)
   {
      HProjectIteration projectIteration = projectIterationDAO.getBySlug(id.getProjectSlug(), id.getVersionSlug());
      return projectIteration.getStatus() == EntityStatus.ACTIVE && projectIteration.getProject().getStatus() == EntityStatus.ACTIVE
            && identity != null && identity.hasPermission("import-template", projectIteration);
   }

   private void checkValidSourceUploadType(DocumentFileUploadForm uploadForm)
   {
      if (!uploadForm.getFileType().equals(".pot")
            && !translationFileServiceImpl.hasAdapterFor(DocumentType.typeFor(uploadForm.getFileType())))
      {
         throw new ChunkUploadException(Status.BAD_REQUEST,
               "The type \"" + uploadForm.getFileType() + "\" specified in form parameter 'type' "
                     + "is not valid for a source file on this server.");
      }
   }

   private static Response sourceUploadSuccessResponse(boolean isNewDocument, int acceptedChunks)
   {
      Response response;
      ChunkUploadResponse uploadResponse = new ChunkUploadResponse();
      uploadResponse.setAcceptedChunks(acceptedChunks);
      uploadResponse.setExpectingMore(false);
      if (isNewDocument)
      {
         uploadResponse.setSuccessMessage("Upload of new source document successful.");
         response = Response.status(Status.CREATED)
               .entity(uploadResponse)
               .build();
      }
      else
      {
         uploadResponse.setSuccessMessage("Upload of new version of source document successful.");
         response = Response.status(Status.OK)
               .entity(uploadResponse)
               .build();
      }
      return response;
   }

   private void processAdapterFile(@Nonnull File tempFile, GlobalDocumentId id, DocumentFileUploadForm uploadForm)
   {
      String name = id.getProjectSlug() + ":" + id.getVersionSlug() + ":" + id.getDocId();
      try
      {
         virusScanner.scan(tempFile, name);
      }
      catch (VirusDetectedException e)
      {
         log.warn("File failed virus scan: {}", e.getMessage());
         throw new ChunkUploadException(Status.BAD_REQUEST, "Uploaded file did not pass virus scan");
      }

      HDocument document;
      Optional<String> params;
      params = Optional.fromNullable(Strings.emptyToNull(uploadForm.getAdapterParams()));
      if (!params.isPresent())
      {
         params = documentDAO.getAdapterParams(id.getProjectSlug(), id.getVersionSlug(), id.getDocId());
      }
      try
      {
         Resource doc = translationFileServiceImpl.parseUpdatedAdapterDocumentFile(tempFile.toURI(), id.getDocId(), uploadForm.getFileType(), params);
         doc.setLang(new LocaleId("en-US"));
         // TODO Copy Trans values
         document = documentServiceImpl.saveDocument(id.getProjectSlug(), id.getVersionSlug(), doc, Collections.<String> emptySet(), false);
      }
      catch (SecurityException e)
      {
         throw new ChunkUploadException(Status.INTERNAL_SERVER_ERROR, e.getMessage(), e);
      }
      catch (ZanataServiceException e)
      {
         throw new ChunkUploadException(Status.INTERNAL_SERVER_ERROR, e.getMessage(), e);
      }

      HRawDocument rawDocument = new HRawDocument();
      rawDocument.setDocument(document);
      rawDocument.setContentHash(uploadForm.getHash());
      rawDocument.setType(DocumentType.typeFor(uploadForm.getFileType()));
      rawDocument.setUploadedBy(identity.getCredentials().getUsername());
      FileInputStream tempFileStream;
      try
      {
         tempFileStream = new FileInputStream(tempFile);
      }
      catch (FileNotFoundException e)
      {
         log.error("Failed to open stream from temp source file", e);
         throw new ChunkUploadException(Status.INTERNAL_SERVER_ERROR,
               "Error saving uploaded document on server, download in original format may fail.\n",
               e);
      }
      LobHelper lobHelper = documentDAO.getLobHelper();
      Blob fileContents = lobHelper.createBlob(tempFileStream, (int) tempFile.length());
      rawDocument.setContent(fileContents);
      if (params.isPresent())
      {
         rawDocument.setAdapterParameters(params.get());
      }
      documentDAO.addRawDocument(document, rawDocument);
      documentDAO.flush();

      translationFileServiceImpl.removeTempFile(tempFile);
   }

   private void parsePotFile(InputStream potStream, GlobalDocumentId id, DocumentFileUploadForm uploadForm)
   {
      Resource doc;
      doc = translationFileServiceImpl.parseUpdatedPotFile(potStream, id.getDocId(), uploadForm.getFileType(),
            useOfflinePo(id));
      doc.setLang(new LocaleId("en-US"));
      // TODO Copy Trans values
      documentServiceImpl.saveDocument(id.getProjectSlug(), id.getVersionSlug(), doc, new StringSet(ExtensionType.GetText.toString()), false);
   }

   private boolean useOfflinePo(GlobalDocumentId id)
   {
      return !isNewDocument(id, documentDAO)
            && !translationFileServiceImpl.isPoDocument(id.getProjectSlug(), id.getVersionSlug(), id.getDocId());
   }

}