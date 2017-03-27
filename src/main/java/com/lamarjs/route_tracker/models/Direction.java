package com.lamarjs.route_tracker.models;

import java.net.MalformedURLException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamarjs.route_tracker.exceptions.BusTimeErrorReceivedException;
import com.lamarjs.route_tracker.services.BustimeAPIRequest;

/**
 * Direction class used to model CTA API direction data of a BusLine. Each
 * direction also contains an associated set of stops.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Direction {

	private String dir; // The name of this direction.
	private List<Stop> stops; // This direction's stops.

	public Direction() {
	};

	/**
	 *
	 * @param dir
	 *            A string representing the direction traveled e.g. "Westbound"
	 * @param stops
	 */
	public Direction(String dir, List<Stop> stops) {
		this.dir = dir;
		this.stops = stops;
	}

	/**
	 * Initializes the stops property for this Direction by requesting the list
	 * of associated bus stops from the CTA API.
	 * 
	 * @throws BusTimeErrorReceivedException
	 *
	 * @throws java.net.MalformedURLException
	 * @throws java.io.IOException
	 */
	public void initializeStops(BustimeAPIRequest requestService, String rt)
			throws MalformedURLException, BusTimeErrorReceivedException {
		stops = requestService.requestStops(rt, dir);
	}

	public String getDirectionName() {
		return dir;
	}

	/**
	 *
	 * @return this Direction's list of stops.
	 */
	public List<Stop> getStops() {
		return stops;
	}

	@JsonProperty(value = "dir")
	public void setDir(String dir) {
		this.dir = dir;
	}

	@JsonProperty(value = "stops")
	public void setStops(List<Stop> stops) {
		this.stops = stops;
	}

	/**
	 *
	 * @return A string representing this direction.
	 */
	@Override
	public String toString() {
		return dir;
	}

}