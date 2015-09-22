/*
 *
 * Copyright 2015 University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 *
 * @author myersjd@umich.edu
 */

package org.sead.api.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.GenericType;
import org.bson.BasicBSONObject;
import org.bson.Document;
import org.bson.types.BasicBSONList;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.sead.api.ResearchObjects;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.util.JSON;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import org.sead.api.util.Constants;

/**
 * See abstract base class for documentation of the rest api. Note - path
 * annotations must match base class for documentation to be correct.
 */

@Path("/researchobjects")
public class ResearchObjectsImpl extends ResearchObjects {
	private MongoClient mongoClient = null;
	private MongoDatabase db = null;
	private MongoCollection<Document> publicationsCollection = null;
	private MongoCollection<Document> peopleCollection = null;
	private MongoCollection<Document> oreMapCollection = null;
	private CacheControl control = new CacheControl();

    private WebResource pdtWebService;
    private WebResource curBeeWebService;

	public ResearchObjectsImpl() {
		mongoClient = new MongoClient();
		db = mongoClient.getDatabase("seadcp");

		publicationsCollection = db.getCollection("researchobjects");
		peopleCollection = db.getCollection("people");
		oreMapCollection = db.getCollection("oreMaps");

		control.setNoCache(true);

        pdtWebService = Client.create().resource(Constants.pdtUrl);
        curBeeWebService = Client.create().resource(Constants.curBeeUrl);
	}

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startROPublicationProcess(String publicationRequestString, @Context HttpServletRequest servletRequest) {
        WebResource webResource = curBeeWebService;

        String requestUrl = servletRequest.getRequestURL().toString();

        ClientResponse response = webResource.path("service/publishRO")
                .queryParam("requestUrl", requestUrl)
                .accept("application/json")
                .type("application/json")
                .post(ClientResponse.class, publicationRequestString);

        return Response.status(response.getStatus()).entity(response.getEntity(new GenericType<String>() {})).build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getROsList() {
        WebResource webResource = pdtWebService;

        ClientResponse response = webResource.path("researchobjects")
                .accept("application/json")
                .type("application/json")
                .get(ClientResponse.class);

        return Response.status(response.getStatus()).entity(response.getEntity(new GenericType<String>() {})).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getROProfile(@PathParam("id") String id) {
        WebResource webResource = pdtWebService;

        ClientResponse response = webResource.path("researchobjects")
                .path(id)
                .accept("application/json")
                .type("application/json")
                .get(ClientResponse.class);

        return Response.status(response.getStatus()).entity(response.getEntity(new GenericType<String>() {})).build();
    }

    @POST
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setROStatus(@PathParam("id") String id, String state) {
        WebResource webResource = pdtWebService;

        ClientResponse response = webResource.path("researchobjects")
                .path(id + "/status")
                .accept("application/json")
                .type("application/json")
                .post(ClientResponse.class, state);

        return Response.status(response.getStatus()).entity(response.getEntity(new GenericType<String>() {})).build();
    }

    @GET
    @Path("/{id}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getROStatus(@PathParam("id") String id) {
        WebResource webResource = pdtWebService;

        ClientResponse response = webResource.path("researchobjects")
                .path(id + "/status")
                .accept("application/json")
                .type("application/json")
                .get(ClientResponse.class);

        return Response.status(response.getStatus()).entity(response.getEntity(new GenericType<String>() {})).build();
    }

	@DELETE
	@Path("/{id}")
	public Response rescindROPublicationRequest(@PathParam("id") String id) {
		//Is there ever a reason to preserve the map and not the pub request?
		//FixMe: Don't allow a delete after the request is complete?
		
		//First remove map
		FindIterable<Document> iter = publicationsCollection.find(new Document(
				"Aggregation.Identifier", id));
		iter.projection(new Document("Aggregation", 1).append("_id", 0));

		Document document = iter.first();
		if(document==null) {
			return Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
		}
		ObjectId mapId = (ObjectId) ((Document)document.get("Aggregation")).get("authoratativeMap");
		
		DeleteResult mapDeleteResult = oreMapCollection.deleteOne(new Document("_id", mapId));
		if (mapDeleteResult.getDeletedCount() != 1) {
			//Report error
			System.out.println("Could not find map corresponding to " + id);
		}
		
		DeleteResult dr = publicationsCollection.deleteOne(new Document(
				"Aggregation.Identifier", id));
		if (dr.getDeletedCount() == 1) {
			return Response.status(Status.OK).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}
    

	@POST
	@Path("/matchingrepositories")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response makeMatches(String matchRequest) {
        // TODO Isuru: call matchmaker API
//		String messageString = null;
//		Document request = Document.parse(matchRequest);
//		Document content = (Document) request.get("Aggregation");
//		if (content == null) {
//			messageString += "Missing Aggregation";
//		}
//		Document preferences = (Document) request.get("Preferences");
//		if (preferences == null) {
//			messageString += "Missing Preferences";
//		}
//		Document stats = (Document) request.get("Aggregation Statistics");
//		if (stats == null) {
//			messageString += "Missing Statistics";
//		}
//
//		if (messageString == null) {
//			// Get organization from profile(s)
//			// Add to base document
//			Object creatorObject = content.get("Creator");
//			String ID = (String) content.get("Identifier");
//
//			BasicBSONList affiliations = new BasicBSONList();
//			if (creatorObject instanceof ArrayList) {
//				Iterator<String> iter = ((ArrayList<String>) creatorObject)
//						.iterator();
//
//				while (iter.hasNext()) {
//					String creator = iter.next();
//					Set<String> orgs = getOrganizationforPerson(creator);
//					if (!orgs.isEmpty()) {
//						affiliations.addAll(orgs);
//					}
//				}
//
//			} else {
//				// BasicDBObject - single value
//				Set<String> orgs = getOrganizationforPerson((String) creatorObject);
//				if (!orgs.isEmpty()) {
//					affiliations.addAll(orgs);
//				}
//			}
//
//			// Get repository profiles
//			FindIterable<Document> iter = db.getCollection("repositories")
//					.find();
//			// iter.projection(new Document("_id", 0));
//
//			// Create result lists per repository
//			// Run matchers
//			MongoCursor<Document> cursor = iter.iterator();
//
//			BasicBSONList matches = new BasicBSONList();
//
//			int j = 0;
//			while (cursor.hasNext()) {
//
//				BasicBSONObject repoMatch = new BasicBSONObject();
//				Document profile = cursor.next();
//
//				repoMatch.put("orgidentifier", profile.get("orgidentifier"));
//
//				BasicBSONList scores = new BasicBSONList();
//				int total = 0;
//				int i = 0;
//				for (Matcher m : matchers) {
//					BasicBSONObject individualScore = new BasicBSONObject();
//
//					RuleResult result = m.runRule(content, affiliations,
//							preferences, stats, profile);
//
//					individualScore.put("Rule Name", m.getName());
//					if (result.wasTriggered()) {
//						individualScore.put("Score", result.getScore());
//						total += result.getScore();
//						individualScore.put("Message", result.getMessage());
//					} else {
//						individualScore.put("Score", 0);
//						individualScore.put("Message", "Not Used");
//					}
//					scores.put(i, individualScore);
//					i++;
//				}
//				repoMatch.put("Per Rule Scores", scores);
//				repoMatch.put("Total Score", total);
//				matches.put(j, repoMatch);
//				j++;
//			}
//			// Assemble and send
//
//			return Response.ok().entity(matches).build();
//		} else {
//			return Response.status(Status.BAD_REQUEST)
//					.entity(new BasicDBObject("Failure", messageString))
//					.build();
//		}
        return Response.ok().entity(null).build();
	}

	@GET
	@Path("/matchingrepositories/rules")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRulesList() {
        // TODO Isuru: Call matchmaker API
//		ArrayList<Document> rulesArrayList = new ArrayList<Document>();
//		for (Matcher m : matchers) {
//			rulesArrayList.add(m.getDescription());
//		}
		return Response.ok().entity(null).build();
	}

	@GET
	@Path("/{id}/oremap")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getROOREMap(@PathParam("id") String id) {;

		FindIterable<Document> iter = publicationsCollection.find(new Document(
				"Aggregation.Identifier", id));
		iter.projection(new Document("Aggregation", 1).append("_id", 0));

		Document document = iter.first();
		if(document==null) {
			return Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
		}
		ObjectId mapId = (ObjectId) ((Document)document.get("Aggregation")).get("authoratativeMap");
		
		iter = oreMapCollection.find(new Document("_id", mapId));
		Document map = iter.first();
		//Internal meaning only
		map.remove("_id");
		return Response.ok(map.toJson()).cacheControl(control).build();
	}

}
