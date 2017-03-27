package com.lamarjs.route_tracker.models;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.lamarjs.route_tracker.TestUtils;
import com.lamarjs.route_tracker.exceptions.BusTimeErrorReceivedException;
import com.lamarjs.route_tracker.services.BustimeAPIRequest;

@RunWith(SpringRunner.class)
@SpringBootTest
public class BusLineTest {

	// TODO: Re-write these tests to align with the new general design. Add more
	// tests.

	static Logger logger;
	static HashMap<String, HashMap<String, String>> sampleFiles;
	BusLine line;

	@Autowired
	BustimeAPIRequest request;

	@BeforeClass
	public static void onlyOnce() throws IOException {
		logger = LoggerFactory.getLogger(BusLineTest.class);
		sampleFiles = TestUtils.loadSampleFiles();

	}

	@Before
	public void setUp() {
		request.setKey(System.getenv("BTRK"));
		/*
		 * Initialize a predefined BusLine object (based on a real BusLine) and
		 * add a set of known directions to it with null for the list of stops
		 * on each direction.
		 */
		line = new BusLine("1", "Bronzeville/Union Station", "#ffffff");

		ArrayList<Direction> directions = new ArrayList<>();
		directions.add(new Direction("Northbound", null));

		line.setDirections(directions);
	}

	@Test
	public void initialize_directions_rt_1_gets_north_south_when_intially_null()
			throws BusTimeErrorReceivedException, MalformedURLException {

		line.setDirections(null);
		line.initializeDirections(request);

		assertTrue(line.getDirections().get(0).getDirectionName().equals("Northbound"));
		assertTrue(line.getDirections().get(1).getDirectionName().equals("Southbound"));
	}

	@Test
	public void initialize_directions_rt_1_overwrites_initial_values()
			throws BusTimeErrorReceivedException, MalformedURLException {
		/*
		 * We null out the directions property and test that it gets initialized
		 * correctly when initializeDirections() is called.
		 * 
		 */
		line.initializeDirections(request);
		assertTrue(line.getDirections().get(0).getDirectionName().equals("Northbound"));
	}

	@Test
	public void direction_initialize_stops_test() throws BusTimeErrorReceivedException, MalformedURLException {
		Direction dir = line.getDirections().get(0);
		dir.initializeStops(request, line.getRouteCode());

		assertTrue("1509 S Michigan".equals(line.getDirections().get(0).getStops().get(0).getStopName()));
	}

	@Test
	public void jsonpath_stops_parse_test() throws MalformedURLException, BusTimeErrorReceivedException {
		Direction dir = line.getDirections().get(0);
		logger.debug("[jsonpath_stops_parse_test()] - dir = " + dir.getDirectionName());

		List<Stop> stops = (List<Stop>) request.requestStops(line.getRouteCode(), dir.getDirectionName());

		assertTrue("1509 S Michigan".equals(stops.get(0).getStopName()));
	}

}
