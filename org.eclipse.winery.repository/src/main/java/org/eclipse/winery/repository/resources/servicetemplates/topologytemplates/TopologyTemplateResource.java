/*******************************************************************************
 * Copyright (c) 2012-2013 University of Stuttgart.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and the Apache License 2.0 which both accompany this distribution,
 * and are available at http://www.eclipse.org/legal/epl-v10.html
 * and http://www.apache.org/licenses/LICENSE-2.0
 *
 * Contributors:
 *     Oliver Kopp - initial API and implementation
 *******************************************************************************/
package org.eclipse.winery.repository.resources.servicetemplates.topologytemplates;

import java.net.URI;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.winery.common.Util;
import org.eclipse.winery.common.ids.definitions.ServiceTemplateId;
import org.eclipse.winery.model.tosca.TNodeTemplate;
import org.eclipse.winery.model.tosca.TRelationshipTemplate;
import org.eclipse.winery.model.tosca.TTopologyTemplate;
import org.eclipse.winery.repository.Prefs;
import org.eclipse.winery.repository.Utils;
import org.eclipse.winery.repository.backend.BackendUtils;
import org.eclipse.winery.repository.client.IWineryRepositoryClient;
import org.eclipse.winery.repository.client.WineryRepositoryClientFactory;
import org.eclipse.winery.repository.json.TopologyTemplateModule;
import org.eclipse.winery.repository.resources.servicetemplates.ServiceTemplateResource;
import org.restdoc.annotations.RestDoc;
import org.restdoc.annotations.RestDocParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.jersey.api.view.Viewable;

public class TopologyTemplateResource {
	
	private static final Logger logger = LoggerFactory.getLogger(TopologyTemplateResource.class);
	
	private final TTopologyTemplate topologyTemplate;
	
	private final ServiceTemplateResource serviceTemplateRes;
	
	
	/**
	 * A topology template is always nested in a service template
	 */
	public TopologyTemplateResource(ServiceTemplateResource parent) {
		this.topologyTemplate = parent.getServiceTemplate().getTopologyTemplate();
		this.serviceTemplateRes = parent;
	}
	
	
	public static class DataForJSP {
		
		private String location;
		private TTopologyTemplate topologyTemplate;
		private URI repositoryURI;
		private String additonalCSS;
		private Boolean autoLayoutOnLoad;
		private String additionalScript;
		
		
		public DataForJSP(String location, URI repositoryURI, TTopologyTemplate topologyTemplate, String additonalCSS, String additionalScript, Boolean autoLayoutOnLoad) {
			this.location = location;
			this.repositoryURI = repositoryURI;
			this.topologyTemplate = topologyTemplate;
			this.additonalCSS = additonalCSS;
			this.additionalScript = additionalScript;
			this.autoLayoutOnLoad = autoLayoutOnLoad;
		}
		
		public String getLocation() {
			return this.location;
		}
		
		public TTopologyTemplate getTopologyTemplate() {
			return this.topologyTemplate;
		}
		
		public String getAdditonalCSS() {
			return this.additonalCSS;
		}
		
		public String getAdditionalScript() {
			return this.additionalScript;
		}
		
		public Boolean getAutoLayoutOnLoad() {
			return this.autoLayoutOnLoad;
		}
		
		public IWineryRepositoryClient getClient() {
			// Quick hack
			// IWineryRepository is not implemented by Prefs.INSTANCE.getRepository()
			// Therefore, we have to generate a real WineryRepositoryClient even if that causes more http load
			IWineryRepositoryClient client = WineryRepositoryClientFactory.getWineryRepositoryClient();
			client.addRepository(this.repositoryURI.toString());
			return client;
		}
		
	}
	
	
	@GET
	@RestDoc(methodDescription = "?edit is used in the URL to get the jsPlumb-based editor")
	@Produces(MediaType.TEXT_HTML)
	// @formatter:off
	public Response getHTML(
			@QueryParam(value = "edit") String edit,
			@QueryParam(value = "script") @RestDocParam(description = "the script to include in a <script> tag. The function wineryViewExternalScriptOnLoad if it is defined. Only available if 'view' is also set") String script,
			@QueryParam(value = "view") String view,
			@QueryParam(value = "autoLayoutOnLoad") String autoLayoutOnLoad,
			@Context UriInfo uriInfo) {
		// @formatter:on
		Response res;
		String JSPName;
		String location = Prefs.INSTANCE.getWineryTopologyModelerPath();
		location = uriInfo.getBaseUri().resolve(location).toString();
		// at winery-light, jersey needs to have an absolute path
		URI repositoryURI = uriInfo.getBaseUri();
		location = location + "/?repositoryURL=";
		location = location + Util.URLencode(repositoryURI.toString());
		ServiceTemplateId serviceTemplate = (ServiceTemplateId) this.serviceTemplateRes.getId();
		location = location + "&ns=";
		location = location + serviceTemplate.getNamespace().getEncoded();
		location = location + "&id=";
		location = location + serviceTemplate.getXmlId().getEncoded();
		if (edit == null) {
			String additionalCSS = null;
			Boolean autoLayoutOnLoadBoolean = false;
			if (view == null) {
				// integration in Winery
				// currently not maintained: Winery includes ?view as iframe
				JSPName = "/jsp/servicetemplates/topologytemplates/topologytemplate.jsp";
			} else {
				// view only mode
				// fullscreen: additionalCSS and script possible
				if (view != "") {
					// view with additional CSS
					URI cssURI = URI.create(view);
					if (cssURI.isAbsolute()) {
						additionalCSS = view;
					} else {
						// relative URLs starts at "/css/topologyrendering/"
						additionalCSS = uriInfo.getBaseUri().resolve("css/topologytemplaterendering/").resolve(view).toString();
						if (!additionalCSS.endsWith(".css")) {
							additionalCSS += ".css";
						}
					}
				}
				if (autoLayoutOnLoad != null) {
					autoLayoutOnLoadBoolean = true;
				}
				JSPName = "/jsp/servicetemplates/topologytemplates/topologytemplateview.jsp";
			}
			Viewable viewable = new Viewable(JSPName, new DataForJSP(location, repositoryURI, this.topologyTemplate, additionalCSS, script, autoLayoutOnLoadBoolean));
			res = Response.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT).entity(viewable).build();
		} else {
			// edit mode
			URI uri = Utils.createURI(location);
			res = Response.seeOther(uri).build();
		}
		return res;
	}
	
	// @formatter:off
	@GET
	@RestDoc(methodDescription="Returns a JSON representation of the topology template. <br />" +
	"X and Y coordinates are embedded as attributes. QName string with Namespace: <br />" +
	"{@link org.eclipse.winery.repository.common.constants.Namespaces.TOSCA_WINERY_EXTENSIONS_NAMESPACE} <br />" +
	"@return The JSON representation of the topology template <em>without</em> associated artifacts and without the parent service template")
	@Produces(MediaType.APPLICATION_JSON)
	// @formatter:on
	public Response getComponentInstanceJSON() {
		Response res;
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.registerModule(new TopologyTemplateModule());
		try {
			// convert it to json
			String json = mapper.writeValueAsString(this.topologyTemplate);
			res = Response.ok(json).build();
		} catch (Exception e) {
			TopologyTemplateResource.logger.error(e.getMessage(), e);
			res = Response.serverError().entity(e.getMessage()).build();
		}
		return res;
	}
	
	@Path("nodetemplates/")
	public NodeTemplatesResource getNodeTemplatesResource() {
		// we illegally convert the List<TEntityTemplate> to List<TNodeTemplate>, because we know
		// that the implementation works nevertheless
		// The alternative is to remove type safety at the resource, which brings more disadvantages
		@SuppressWarnings("unchecked")
		List<TNodeTemplate> l = (List<TNodeTemplate>) (List<?>) this.topologyTemplate.getNodeTemplateOrRelationshipTemplate();
		return new NodeTemplatesResource(l, this.topologyTemplate, this.serviceTemplateRes);
	}
	
	@Path("relationshiptemplates/")
	public RelationshipTemplatesResource getRelationshipTemplatesResource() {
		// we illegally convert the List<TEntityTemplate> to List<TRelationshipTemplate>, because we know
		// that the implementation works nevertheless
		// The alternative is to remove type safety at the resource, which brings more disadvantages
		@SuppressWarnings("unchecked")
		List<TRelationshipTemplate> l = (List<TRelationshipTemplate>) (List<?>) this.topologyTemplate.getNodeTemplateOrRelationshipTemplate();
		return new RelationshipTemplatesResource(l, this.topologyTemplate, this.serviceTemplateRes);
	}
	
	@PUT
	@RestDoc(methodDescription = "Replaces the topology by the information given in the XML")
	@Consumes(MediaType.TEXT_XML)
	public Response setModel(TTopologyTemplate topologyTemplate) {
		this.serviceTemplateRes.getServiceTemplate().setTopologyTemplate(topologyTemplate);
		return BackendUtils.persist(this.serviceTemplateRes);
	}
	
	// @formatter:off
	@GET
	@RestDoc(methodDescription="<p>Returns an XML representation of the topology template." +
	" X and Y coordinates are embedded as attributes. Namespace:" +
	"{@link org.eclipse.winery.repository.common.constants.Namespaces.TOSCA_WINERY_EXTENSIONS_NAMESPACE} </p>" +
	"<p>{@link org.eclipse.winery.repository.client.WineryRepositoryClient." +
	"getTopologyTemplate(QName)} consumes this template</p>" +
	"<p>@return The XML representation of the topology template <em>without</em>" +
	"associated artifacts and without the parent service template </p>")
	@Produces(MediaType.TEXT_XML)
	// @formatter:on
	public Response getComponentInstanceXML() {
		return Utils.getXML(TTopologyTemplate.class, this.topologyTemplate);
	}
	
}