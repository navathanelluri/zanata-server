package org.zanata.dao;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HProject;

@Name("accountRoleDAO")
@AutoCreate
@Scope(ScopeType.STATELESS)
public class AccountRoleDAO extends AbstractDAOImpl<HAccountRole, Integer>
{

   public AccountRoleDAO()
   {
      super(HAccountRole.class);
   }

   public AccountRoleDAO(Session session)
   {
      super(HAccountRole.class, session);
   }

   public boolean roleExists(String role)
   {
      return findByName(role) != null;
   }

   public HAccountRole findByName(String roleName)
   {
      Criteria cr = getSession().createCriteria(HAccountRole.class);
      cr.add(Restrictions.eq("name", roleName));
      cr.setCacheable(true).setComment("AccountRoleDAO.findByName");
      return (HAccountRole) cr.uniqueResult();
   }

   public HAccountRole create(String roleName, HAccountRole.RoleType type, String... includesRoles)
   {
      HAccountRole role = new HAccountRole();
      role.setName(roleName);
      role.setRoleType(type);
      for (String includeRole : includesRoles)
      {
         Set<HAccountRole> groups = role.getGroups();
         if (groups == null)
         {
            groups = new HashSet<HAccountRole>();
            role.setGroups(groups);
         }
         groups.add(findByName(includeRole));
      }
      makePersistent(role);
      return role;
   }

   public List<HAccount> listMembers(String roleName)
   {
      HAccountRole role = findByName(roleName);
      return listMembers(role);
   }

   @SuppressWarnings("unchecked")
   public List<HAccount> listMembers(HAccountRole role)
   {
      Query query = getSession().createQuery("from HAccount account where :role member of account.roles");
      query.setParameter("role", role);
      query.setComment("AccountRoleDAO.listMembers");
      return query.list();
   }

   public Collection<HAccountRole> getByProject( HProject project )
   {
      return getSession()
            .createQuery("select p.allowedRoles from HProject p where p = :project")
            .setParameter("project", project)
            .setComment("AccountRoleDAO.getByProject")
            .list();
   }

}
