package org.aigua.web;

import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
 
import org.springframework.ui.ModelMap;

import javax.servlet.http.HttpServletRequest;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;

import org.apache.log4j.Logger;

import java.util.Map;
import java.util.HashMap;
import java.util.Date; 
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.net.URLDecoder;
import java.net.URL;

import org.aigua.domain.User;
import org.aigua.dao.UserDao;

import org.aigua.domain.Role;
import org.aigua.dao.RoleDao;

import static org.aigua.common.ShiroHaiConstants.*;

@Controller
@RequestMapping("/user")
public class UserController {

	private static final Logger log = Logger.getLogger(UserController.class.getName());
		
	@Autowired
	private UserDao userDao;	
		
	@Autowired
	private RoleDao roleDao;	
	
	
	private static int RESULTS_PER_PAGE = 10;
	private static String USER_EDIT = "user:edit";
	private static String USER_UPDATE = "user:update";
	private static String DELIM = ":";
	

	@RequestMapping(value="/create", method=RequestMethod.GET)
	public String create(ModelMap model, HttpServletRequest request){
		
		if (!SecurityUtils.getSubject().hasRole(ADMIN_ROLE)){
	    	log.debug("\n\nOperation not permitted");
	      	throw new AuthorizationException("No Permission"); 
	    }
	
		model.addAttribute("title", "Create New User");
		model.addAttribute("addUserActive", "active");
		return "user/create";
	}



	@RequestMapping(method=RequestMethod.POST)
	public String saveUser(ModelMap model, HttpServletRequest request){
		
		try {
			
			log.debug(request);
			
			String name = request.getParameter("name");
			String email = request.getParameter("email");
			String username = request.getParameter("username");
			

			User user = new User();
			user.setName(name);
			user.setEmail(email);
			user.setUsername(username);
			user.setPasswordHash(DEFAULT_PASSWORD);
			
			log.debug(user);
			
			//save user
			User savedUser = userDao.save(user);		
			model.addAttribute("user", savedUser);
			model.addAttribute("message", "successfully saved user : " + savedUser.getId());
			
			//save user roles
			Role defaultRole = roleDao.findByName(CUSTOMER_ROLE);
			userDao.saveUserRole(savedUser.getId(), defaultRole.getId());
			
			userDao.saveUserPermission(savedUser.getId(), USER_EDIT + DELIM + savedUser.getId());
			userDao.saveUserPermission(savedUser.getId(), USER_UPDATE + DELIM + savedUser.getId());
			
			
		}catch(Exception e){
			e.printStackTrace();
		}

		return "redirect:user/list";
	}
	


	@RequestMapping(value="/{id}/update", method=RequestMethod.POST)
	public String updateUser(ModelMap model,
					   HttpServletRequest request,
					   final RedirectAttributes redirect){
						   
		try {
			
			String id = request.getParameter("id");
			String name = request.getParameter("name");
			String email = request.getParameter("email");
			String username = request.getParameter("username");
			String password = request.getParameter("passwordHash");
			
			if (isCustomerWithPermission(USER_UPDATE, id) || SecurityUtils.getSubject().hasRole(ADMIN_ROLE)){
				
				User user = new User();
				user.setId(Integer.parseInt(id));
				user.setName(name);
				user.setEmail(email);
				user.setUsername(username);
				user.setPasswordHash(password);

				userDao.update(user);
			
				User updatedUser = userDao.findById(Integer.parseInt(id));
				redirect.addFlashAttribute("user", updatedUser);
				redirect.addFlashAttribute("message", "successfully updated user : " + updatedUser.getId());
				
			}else{
		    	log.debug("\n\nOperation not permitted");
		      	throw new AuthorizationException("No Permission"); 
			}
			

			
		}catch(Exception e){
			e.printStackTrace();
		}

		return "redirect:/app/user/list";
	}

		
	
	@RequestMapping(value="/{id}", method=RequestMethod.DELETE)
	public String deleteUser(ModelMap model, 
						     @PathVariable String id,
					   		 final RedirectAttributes redirect){
			
		if (!SecurityUtils.getSubject().hasRole(ADMIN_ROLE)){
			log.debug("\n\nOperation not permitted");
		  	throw new AuthorizationException("No Permission"); 
		}
								
		User user = userDao.findById(Integer.parseInt(id));	
		userDao.delete(user.getId());
		
	 	redirect.addFlashAttribute("user", user);
		redirect.addFlashAttribute("message", "successfully deleted user : " + user.getId());


		return "redirect:/app/user/list";
		
	}
		
	
	@RequestMapping(value="/show/{id}", method=RequestMethod.GET)
	public String show(ModelMap model,
					   HttpServletRequest request, 
					   @PathVariable String id){
		
		User user = userDao.findById(Integer.parseInt(id));
		
		Set<String> roles = userDao.getUserRoles(user.getId());
		Set<String> permissions = userDao.getUserPermissions(user.getId());
		
		if(roles != null && !roles.isEmpty()) user.setRoles(roles);
		if(permissions != null && !permissions.isEmpty()) user.setPermissions(permissions);
		
		model.addAttribute("title", "Show User : " + id);
		model.addAttribute("user", user);
		
		return "user/show";
	}	


	@RequestMapping(value="/edit/{id}", method=RequestMethod.GET)
	public String edit(ModelMap model,
					   HttpServletRequest request, 
					   @PathVariable String id){
		
		if (isCustomerWithPermission(USER_EDIT, id) || SecurityUtils.getSubject().hasRole(ADMIN_ROLE)){
		
			User user = userDao.findById(Integer.parseInt(id));
			
			Set<String> roles = userDao.getUserRoles(user.getId());
			Set<String> permissions = userDao.getUserPermissions(user.getId());
		
			if(roles != null && !roles.isEmpty()) user.setRoles(roles);
			if(permissions != null && !permissions.isEmpty()) user.setPermissions(permissions);
		
			model.addAttribute("title", "Edit User : " + id);
			model.addAttribute("user", user);
			
		}else{
			log.debug("\nOperation not permitted");
		  	throw new AuthorizationException("No Permission"); 
		}

		
		return "user/edit";
	}	



	@RequestMapping(value="/list", method=RequestMethod.GET)
	public String list(ModelMap model, 
				       HttpServletRequest request, 
					   @RequestParam(value="offset", required = false ) String offset,
					   @RequestParam(value="max", required = false ) String max,
					   @RequestParam(value="page", required = false ) String page){
		
		
		if(page == null){
			page = "1";
		}						
		
		List<User> users;
		
		if(offset != null) {
			int m = RESULTS_PER_PAGE;
			if(max != null){
				m = Integer.parseInt(max);
			}
			int o = Integer.parseInt(offset);
			users = userDao.findAllOffset(m, o);	
		}else{
			users = userDao.findAll();	
		} 
		
		int count = userDao.count();
		
		model.addAttribute("users", users);
		model.addAttribute("total", count);
		
		model.addAttribute("title", "List Properties");
		model.addAttribute("resultsPerPage", RESULTS_PER_PAGE);
		model.addAttribute("activePage", page);
		
		return "user/list";
	}
	
		
	private boolean isCustomerWithPermission(String permission, String id){
		log.debug("is customer : " + SecurityUtils.getSubject().hasRole(CUSTOMER_ROLE) + " && has permission " + permission + DELIM + id + " : " + SecurityUtils.getSubject().isPermitted(permission + DELIM + id));
		return SecurityUtils.getSubject().hasRole(CUSTOMER_ROLE) && 
			SecurityUtils.getSubject().isPermitted(permission + DELIM + id);
		
	}
		

	
}