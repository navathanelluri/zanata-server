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
      MavenDependencyResolver mavenResolver = DependencyResolvers.use(MavenDependencyResolver.class)
            .configureFrom("/home/camunoz/.m2/settings.xml")
            .loadMetadataFromPom("pom.xml");
      
      WebArchive archive = 
            ShrinkWrap.create(WebArchive.class, "zanata.war")
               // Web classes
               .addPackages(true, "org.h2.tools", "org.zanata")
               // Maven dependencies
               .addAsLibraries(mavenResolver.includeDependenciesFromPom("pom.xml").resolveAsFiles( 
                  new MavenResolutionFilter()
                  {
                     
                     @Override
                     public MavenResolutionFilter configure(Collection<MavenDependency> dependencies)
                     {
                        return this;
                     }
                     
                     @Override
                     public boolean accept(MavenDependency element)
                     {
                        // Ignore all Jboss AS related dependencies
                        if(element.getCoordinates().startsWith("jboss") ||
                           element.getCoordinates().startsWith("org.jboss:") ||
                           element.getCoordinates().startsWith("org.jboss.jbossas") ||
                           element.getCoordinates().startsWith("org.jboss.javaee") ||
                           element.getCoordinates().startsWith("org.jboss.integration") ||
                           element.getCoordinates().startsWith("org.jboss.logging") ||
                           element.getCoordinates().startsWith("org.jboss.metadata") ||
                           element.getCoordinates().startsWith("org.jboss.ws.native") )
                        {
                           return false;
                        }
                        // Ignore apache xerces
                        if(element.getCoordinates().startsWith("apache-xerces:"))
                        {
                           return false;
                        }
                        // Add Arquillian needed deps
                        if( element.getCoordinates().startsWith("org.jboss.shrinkwrap.resolver") )
                        {
                           return true;
                        }
                        // Add third-party test dependencies needed on the server side when running the test
                        if( element.getCoordinates().startsWith("org.hamcrest") )
                        {
                           return true;
                        }
                        
                        if(element.getScope().equalsIgnoreCase("compile") || element.getScope().equalsIgnoreCase("runtime"))
                        {
                           return true;
                        }
                        
                        return false;
                     }
                  } 
               ))
               /*.addAsLibraries(mavenResolver.artifact("org.zanata:zanata-common-api").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.zanata:zanata-common-util").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.zanata:zanata-model").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.zanata:zanata-adapter-po").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam-ui").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam-debug").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam-mail").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam-remoting").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.seam:jboss-seam-resteasy").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.resteasy:resteasy-jaxrs").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.resteasy:resteasy-jaxb-provider").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.jboss.resteasy:resteasy-jackson-provider").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.codehaus.jackson:jackson-core-asl").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.codehaus.jackson:jackson-mapper-asl").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.codehaus.jackson:jackson-jaxrs").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.codehaus.jackson:jackson-xc").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.drools:drools-core").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.drools:drools-compiler").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.richfaces.framework:richfaces-api").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.richfaces.framework:richfaces-impl").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.richfaces.ui:richfaces-ui").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.hibernate:hibernate-search").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("net.sf.ehcache:ehcache-core").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.dbunit:dbunit").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.tuckey:urlrewritefilter").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("com.ibm.icu:icu4j").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.slf4j:slf4j-api").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.slf4j:slf4j-log4j12").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("com.google.guava:guava").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.fedorahosted.openprops:openprops").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.codehaus.enunciate:enunciate-core-annotations").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.apache.lucene:lucene-core").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("com.google.gwt.inject:gin").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("com.google.inject:guice").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("net.customware.gwt.dispatch:gwt-dispatch").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("com.allen-sauer.gwt.log:gwt-log").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("de.novanic.gwteventservice:gwteventservice").resolveAsFiles())
               .addAsLibraries(mavenResolver.artifact("org.liquibase:liquibase-core").resolveAsFiles())*/
               // Descriptors
               .setWebXML(new File("src/test/resources/arquillian/web.xml"))
               .addAsWebInfResource(new File("src/test/resources/arquillian/jboss-web.xml"))
               .addAsWebInfResource(new File("src/test/resources/arquillian/META-INF/persistence.xml"), "classes/META-INF/persistence.xml")
               .addAsWebInfResource(new File("src/test/resources/arquillian/META-INF/components.xml"), "classes/META-INF/components.xml")
               .addAsWebInfResource(new File("src/main/resources/META-INF/ehcache.xml"), "classes/META-INF/ehcache.xml")
               .addAsWebInfResource(new File("src/main/resources/META-INF/orm.xml"), "classes/META-INF/orm.xml")
               .addAsWebInfResource(new File("src/main/resources/META-INF/seam-deployment.properties"), "classes/META-INF/seam-deployment.properties")
               .addAsWebInfResource(new File("src/main/resources/seam.properties"), "classes/seam.properties")
               .addAsWebInfResource(new File("src/main/resources/security.drl"), "classes/security.drl")
               .addAsWebInfResource(new File("src/test/resources/arquillian/components.properties"), "classes/components.properties")
               // Liquibase changelogs
               // TODO Import changelogs without having to specify each one
               .addAsWebInfResource(new File("src/main/resources/db/db.changelog.xml"), "classes/db/db.changelog.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.0.xml"), "classes/db/changelogs/db.changelog-1.0.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.2.xml"), "classes/db/changelogs/db.changelog-1.2.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.3.xml"), "classes/db/changelogs/db.changelog-1.3.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.4.xml"), "classes/db/changelogs/db.changelog-1.4.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.5.xml"), "classes/db/changelogs/db.changelog-1.5.xml")
               .addAsWebInfResource(new File("src/main/resources/db/changelogs/db.changelog-1.6.xml"), "classes/db/changelogs/db.changelog-1.6.xml")
               // H2 Triggers
               .addAsWebInfResource(new File("src/main/resources/db/h2/create_trigger.sql"), "classes/db/h2/create_trigger.sql")
               .addAsWebInfResource(new File("src/main/resources/db/h2/h2_baseline.sql"), "classes/db/h2/h2_baseline.sql")
               // Mysql Triggers (still need them even if not using them)
               .addAsWebInfResource(new File("src/main/resources/db/mysql/create_trigger.sql"), "classes/db/mysql/create_trigger.sql")
               .addAsWebInfResource(new File("src/main/resources/db/mysql/mysql_baseline.sql"), "classes/db/mysql/mysql_baseline.sql");
      
      
      // Web pages
      archive.as( ExplodedImporter.class )
               .importDirectory( new File("src/main/webapp") );
      
      // Export
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
