package com.lamarjs.route_tracker.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.lamarjs.route_tracker.exceptions.BusTimeErrorReceivedException;
import com.lamarjs.route_tracker.models.BusLine;
import com.lamarjs.route_tracker.models.Direction;
import com.lamarjs.route_tracker.models.Prediction;
import com.lamarjs.route_tracker.models.Stop;

import lombok.Data;

/**
 * This class represents a request to (and response from) the CTA Bustime
 * (BusTracker) API. It also provides the methods needed to build then send the
 * requests as well as parse the json response's returned by them into the
 * associated objets when provided. The enums {@link RequestType} and
 * {@link Parameter} represent the legal components of any valid request to the
 * Bustime API.
 * 
 * @author Lamar J. Smith
 */
@Service
@Data
public class BustimeAPIRequest {

	// Base request components
	/**
	 * This is the base component of the CTA API's request URL. The key and any
	 * parameters that follow it are appended to the end of this string.
	 */
	public static final String BUSTIME_REQUEST_BASE = "http://ctabustracker.com/bustime/api/v2/";

	/**
	 * The API key, which can be stored as an environment variable on the
	 * server, or set explicitly.
	 */
	public static final String API_KEY = "?key=";

	/**
	 * This is a parameter that can be added to the request to get back a JSON
	 * response instead of XML. This utility class has methods for handling the
	 * parsing of JSON responses into equivalent objects. This class does not
	 * implement any helper methods for parsing the XML version of the responses
	 * so it is generally a good idea to include this parameter when the option
	 * is given.
	 */
	public static final String F_JSON = "&format=json";

	// RequestURL Parameter Formats
	public enum Parameter {
		/**
		 * The route parameter indicates a specific route for those requests
		 * that require one as input.
		 */
		ROUTE("&rt="),

		/**
		 * The direction parameter indicates a specific direction traveled by a
		 * specified route.
		 */
		DIRECTION("&dir="),

		/**
		 * The stop id parameter indicates a single stop along a specific
		 * direction traveled by a specified route.
		 */
		STOPID("&stpid="),

		/**
		 * The limit parameter indicates the total number of predictions to be
		 * returned by a "predictions" request.
		 */
		LIMIT("&top=");

		private final String Format;

		private Parameter(String param) {
			this.Format = param;
		}

		public String format() {
			return Format;
		}
	}

	// Request types
	public enum RequestType {
		/**
		 * Returns a list of BusLines available.
		 */
		ROUTES("getroutes"),

		/**
		 * Returns a list of directions for the specified route.
		 */
		DIRECTIONS("getdirections"),

		/**
		 * Returns a list of stops for the specified route and direction
		 * combination.
		 */
		STOPS("getstops"),

		/**
		 * Returns a set of ETAs for a specific stop along a bus route.
		 */
		PREDICTIONS("getpredictions");

		private final String format;

		private RequestType(String type) {
			this.format = type;
		}

		public String format() {
			return format;
		}
	}

	// Properties
	private RestTemplateBuilder templateBuilder;
	private Configuration jsonPathConfig;
	private URL requestURL; // The request URL.
	private Object responseBody; // The response returned by the CTA API as a
									// parsed Json document.
	private String key; // The API key component of a request that can be set as
						// an environment variable or explicitly set.

	// Constructors

	/**
	 * Creates a BusTimeAPIRequest object. The supplied builder methods
	 * construct legal API request URLs for the various end-points that the CTA
	 * has made available. Once a requestURL has been created, the
	 * {@link send()} method can be called to send the requestURL to the CTA
	 * API.
	 */
	public BustimeAPIRequest() {
		// The key to be inserted into the request URL key parameter should be
		// set as an environment variable.
		key = System.getenv("BTRK");
	}

	@Autowired
	public BustimeAPIRequest(RestTemplateBuilder templateBuilder, Configuration jsonPathConfig) {
		key = System.getenv("BTRK");
		this.templateBuilder = templateBuilder;
		this.jsonPathConfig = jsonPathConfig;
	}

	/**
	 * Convenience method primarily for testing. Allows a key to be specified as
	 * an argument instead of as an environment variable.
	 * 
	 * @param key
	 *            Required to make CTA API calls. Can optionally be provided as
	 *            an environment variable using the no-arg constructor
	 *            {@link BustimeAPIRequest()}
	 */
	public BustimeAPIRequest(String key) {
		this.key = key;
	}

	/**
	 * Convenience method primarily for testing. Allows a requestURL to be
	 * specified as an argument instead of building it with the helper methods
	 * after creation of the instance.
	 * 
	 * @param requestURL
	 *            The request url for the CTA API. The send() method has to be
	 *            called on this instance to actually pass the request and
	 *            retrieve the data for the responseBody.
	 *
	 */
	public BustimeAPIRequest(URL requestURL, String key) {
		this.requestURL = requestURL;
	}

	/**
	 * Builds a well formated request url for the CTA API. The enums:
	 * {@link Parameter} and {@link RequestType} in this class provide the
	 * proper request name and format for the parameters that make up the final
	 * URL.
	 * 
	 * @param requestType
	 *            {@link Parameter} representing the request type to made.
	 * @param urlParameters
	 *            A map of <{@link Parameter}, {@linkString}> pairs that each
	 *            represent a parameter and value to be included in the
	 *            requestURL.
	 * @throws MalformedURLException
	 */
	public BustimeAPIRequest buildRequestURL(RequestType requestType, Map<Parameter, String> urlParameters,
			Boolean returnJson) throws MalformedURLException {

		StringBuilder paramsBuilder = new StringBuilder();
		for (Parameter param : urlParameters.keySet()) {
			paramsBuilder.append(param.Format).append(urlParameters.get(param));
		}
		paramsBuilder.append(returnJson ? F_JSON : "");

		StringBuilder requestBuilder = new StringBuilder(BUSTIME_REQUEST_BASE).append(requestType.format)
				.append(API_KEY).append(key).append(paramsBuilder.toString());
		requestURL = new URL(requestBuilder.toString());
		return this;
	}

	/**
	 * Builds a well formated request url for the CTA API. The enums:
	 * {@link Parameter} and {@link RequestType} in this class provide the
	 * proper request name and format for the parameters that make up the final
	 * URL. <br>
	 * <br>
	 * <b>Ex:</b> buildRequestURL(Request.STOPS, Parameter.ROUTE + "x9" +
	 * Parameter.DIRECTION + BusLine.SOUTH) will return a request URL for a list
	 * of stops along the South Bound X9 - Express Ashland bus.
	 * 
	 * @param requestType
	 * @param urlParameters
	 * @param returnJson
	 *            Requests response as json
	 * @throws MalformedURLException
	 */
	public BustimeAPIRequest buildRequestURL(RequestType requestType, String urlParameters, Boolean returnJson)
			throws MalformedURLException {

		StringBuilder requestBuilder = new StringBuilder(BUSTIME_REQUEST_BASE).append(requestType.format)
				.append(API_KEY).append(key).append(urlParameters).append(returnJson ? F_JSON : "");

		requestURL = new URL(requestBuilder.toString());
		return this;
	}

	/**
	 * Convenience method similar to
	 * {@link BustimeAPIRequest#buildRequestURL(RequestType, String, Boolean)}
	 * but with Json return type enabled by default.
	 * 
	 * @see BustimeAPIRequest#buildRequestURL(RequestType, String, Boolean)
	 * @param requestType
	 * @param urlParameters
	 * @return
	 * @throws MalformedURLException
	 */
	public BustimeAPIRequest buildRequestURL(RequestType requestType, String urlParameters)
			throws MalformedURLException {
		return this.buildRequestURL(requestType, urlParameters, true);
	}

	/**
	 * Convenience method to build a valid Routes ("getRoutes") requestURL
	 * 
	 * @param requestType
	 * @param urlParameters
	 * @throws MalformedURLException
	 */
	public BustimeAPIRequest buildRoutesRequestURL() throws MalformedURLException {
		return buildRequestURL(RequestType.ROUTES, "", true);
	}

	/**
	 * Sends the requestURL. A proper URL can be built using
	 * {@link buildRequestURL()}, {@link buildGetRoutesRequest()}, or
	 * {@link setRequestURL()}. This method populates the responseBody with the
	 * Json returned by the CTA API.
	 * 
	 * @throws BusTimeErrorReceivedException
	 *             if an error message is returned in the CTA response.
	 */
	public BustimeAPIRequest send(URL requestURL) {

		RestTemplate template = templateBuilder.build();

		ResponseEntity<String> responseEntity = template.exchange(requestURL.toString(), HttpMethod.GET, null,
				String.class);

		responseBody = jsonPathConfig.jsonProvider().parse(responseEntity.getBody());

		return this;
	}

	public BustimeAPIRequest send() {
		return send(requestURL);
	}

	public String getBustimeError(Object responseBody) {
		String error;
		try {
			error = JsonPath.read(responseBody, "$.bustime-response.error[0].msg");

		} catch (PathNotFoundException e) {
			return null;
		}

		return error;
	}

	/**
	 * Requests a list of all operating BusLines (routes).
	 * 
	 * @return A list of BusLine objects that represents all routes serviced by
	 *         the CTA. Further initialization is required for each BusLine
	 *         object by calling each instances initialize() method.
	 * 
	 * @throws BusTimeErrorReceivedException
	 *             if the response from the CTA includes an error message.
	 * @throws URISyntaxException
	 * @throws RestClientException
	 */
	public List<BusLine> requestRoutes(URL requestURL)
			throws BusTimeErrorReceivedException, RestClientException, URISyntaxException {
		send(requestURL);

		String error = getBustimeError(responseBody);
		if (error != null) {
			throw new BusTimeErrorReceivedException(error);
		}

		return JsonPath.using(jsonPathConfig).parse(responseBody).read("$.bustime-response.routes[*]",
				new TypeRef<List<BusLine>>() {
				});
	}

	public List<BusLine> requestRoutes()
			throws BusTimeErrorReceivedException, MalformedURLException, RestClientException, URISyntaxException {

		buildRoutesRequestURL();
		return requestRoutes(requestURL);
	}

	/**
	 * Requests a list of directions along the from the CTA API for the given
	 * BusLine
	 * 
	 * @param busline
	 *            The BusLine object that the directions request should be
	 *            handled for
	 * @return A list of Direction objects i.e. ["Northbound", "Southbound"]
	 *         that the provided BusLine object services.
	 * @throws BusTimeErrorReceivedException
	 * @throws IOException
	 * 
	 * @throws MalformedURLException
	 */
	public List<Direction> requestDirections(String routeCode)
			throws MalformedURLException, BusTimeErrorReceivedException {

		// Build the directions request
		buildRequestURL(RequestType.DIRECTIONS, Parameter.ROUTE.Format + routeCode);
		send();

		String error = getBustimeError(responseBody);
		if (error != null) {
			throw new BusTimeErrorReceivedException(error);
		}

		return JsonPath.using(jsonPathConfig).parse(responseBody).read("$.bustime-response.directions[*]",
				new TypeRef<List<Direction>>() {
				});
	}

	/**
	 * Requests a list of stops along the given route code and Direction object
	 * from the CTA API.
	 *
	 * @param rt
	 *            The route code for the route that the direction is associated
	 *            with
	 * @param direction
	 *            The direction along the route that stops should be requested
	 *            for.
	 *
	 * @throws MalformedURLException
	 * @throws BusTimeErrorReceivedException
	 * 
	 */
	public List<Stop> requestStops(String rt, String direction)
			throws MalformedURLException, BusTimeErrorReceivedException {

		// Build the stops request
		StringBuilder paramsBuilder = new StringBuilder(Parameter.ROUTE.Format).append(rt)
				.append(Parameter.DIRECTION.Format).append(direction);

		buildRequestURL(RequestType.STOPS, paramsBuilder.toString());
		send();

		String error = getBustimeError(responseBody);
		if (error != null) {
			throw new BusTimeErrorReceivedException(error);
		}

		// Parse the response into a stops list.
		return JsonPath.using(jsonPathConfig).parse(responseBody).read("$.bustime-response.stops[*]",
				new TypeRef<List<Stop>>() {
				});
	}

	/**
	 * Requests a list of stops along the given route code and Direction object
	 * from the CTA API.
	 * 
	 * @param top
	 *
	 * @param rt
	 *            The route code for the route that the direction is associated
	 *            with
	 * @param direction
	 *            The direction along the route that stops should be requested
	 *            for.
	 *
	 * @throws MalformedURLException
	 * @throws BusTimeErrorReceivedException
	 * @throws IOException
	 */
	public List<Prediction> requestPredictions(String stpids, String rts, int top)
			throws MalformedURLException, BusTimeErrorReceivedException {

		// Format the passed in stop ids and route codes, and top (limit)
		StringBuilder paramsBuilder = new StringBuilder(Parameter.STOPID.Format).append(stpids)
				.append(Parameter.ROUTE.Format).append(rts).append(Parameter.LIMIT.Format).append(top);

		buildRequestURL(RequestType.PREDICTIONS, paramsBuilder.toString());
		send();

		String error = getBustimeError(responseBody);
		if (error != null) {
			throw new BusTimeErrorReceivedException(error);
		}

		// Parse the response into a stops list.
		return JsonPath.using(jsonPathConfig).parse(responseBody).read("$.bustime-response.prd[*]",
				new TypeRef<List<Prediction>>() {
				});
	}
}