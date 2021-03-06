package org.zanata.file;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = { "unit-tests" })
public class GlobalDocumentIdTest
{

   private static final String PROJECT_SLUG = "project";
   private static final String VERSION_SLUG = "version";
   private static final String DOCUMENT_ID = "document";

   private GlobalDocumentId id;

   @BeforeMethod
   public void setup()
   {
      id = new GlobalDocumentId(PROJECT_SLUG, VERSION_SLUG, DOCUMENT_ID);
   }

   public void getDocId()
   {
      assertThat(id.getDocId(), is(DOCUMENT_ID));
   }

   public void getIterationSlug()
   {
      assertThat(id.getVersionSlug(), is(VERSION_SLUG));
   }

   public void getProjectSlug()
   {
      assertThat(id.getProjectSlug(), is(PROJECT_SLUG));
   }
}
