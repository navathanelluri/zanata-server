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
package org.zanata.arquillian.descriptor;

import org.jboss.shrinkwrap.descriptor.spi.node.Node;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeDescriptorImplBase;

/**
 * Implementation of Arquillian's descriptor interface to deploy a Data Source to JBoss 5.
 * 
 * Currently only supports Local Transaction Data Sources.
 * 
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public class JBoss5DataSourceDescriptor extends NodeDescriptorImplBase
{

   private String jndiName;
   
   private String connectionUrl;
   
   private String driverClass;
   
   private String userName;
   
   private String password;
   
   
   
   public JBoss5DataSourceDescriptor(String descriptorName, String jndiName, String connectionUrl, String driverClass, String userName, String password)
   {
      super(descriptorName);
      this.jndiName = jndiName;
      this.connectionUrl = connectionUrl;
      this.driverClass = driverClass;
      this.userName = userName;
      this.password = password;
   }

   @Override
   public Node getRootNode()
   {
      Node root = new Node("datasources");
      Node datasource = root.createChild("local-tx-datasource");
      datasource.createChild("jndi-name").text( this.jndiName != null ? this.jndiName : "" );
      datasource.createChild("connection-url").text( this.connectionUrl != null ? this.connectionUrl : "" );
      datasource.createChild("driver-class").text( this.driverClass != null ? this.driverClass : "" );
      datasource.createChild("user-name").text( this.userName != null ? this.userName : "" );
      datasource.createChild("password").text( this.password != null ? this.password : "" );
      
      return root;
   }

}
