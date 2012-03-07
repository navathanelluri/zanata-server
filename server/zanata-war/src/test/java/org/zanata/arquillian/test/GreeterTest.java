/*
 * Copyright 2010, Red Hat, Inc. and individual contributors
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
package org.zanata.arquillian.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.seam.annotations.In;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ExplodedImporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.resolver.api.DependencyResolvers;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolutionFilter;
import org.testng.annotations.Test;
import org.zanata.arquillian.descriptor.JBoss5DataSourceDescriptor;
import org.zanata.dao.PersonDAO;
import org.zanata.model.HPerson;

public class GreeterTest extends Arquillian
{

   @In
   PersonDAO personDAO;
   
   @Deployment(order = 1, name = "zanata-test-ds.xml")
   public static Descriptor createDataSourceDeployment()
   {
      return new JBoss5DataSourceDescriptor(
            "zanata-test-ds.xml", 
            "zanataTestDatasource", 
            "jdbc:h2:mem:zanata;DB_CLOSE_DELAY=-1", 
            "org.h2.Driver", 
            "sa", 
            null);
   }
   
   @Deployment(order = 2, name = "zanata.war")
   public static Archive<?> createDeployment()
   {  
      // Assume there is already a target directory called zanata-seamtests
      WebArchive archive = 
            ShrinkWrap.create(WebArchive.class, "zanata.war");
      
      archive.as(ExplodedImporter.class).importDirectory("target/zanata-seamtests");
      
      // Add any test scoped dependencies that might not be in the original archive
      archive.addPackages(true, "org.hamcrest");
      
      // Export (to actually see what is being deployed)
      //archive.as(ZipExporter.class).exportTo( new File("/home/camunoz/temp/archive.war"), true );
      
      return archive;
   }
   
   @Test
   public void testHelloWorld()
   {
      List<HPerson> people = this.personDAO.findAll();
      assertThat(people.size(), greaterThan(0));
   }
   
}
