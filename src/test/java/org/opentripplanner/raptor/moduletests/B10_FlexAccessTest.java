package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.flex;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.flexAndWalk;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_STANDARD_ONE;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.standard;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;
import org.opentripplanner.raptor.spi.DefaultSlackProvider;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.RaptorCostConverter;

/**
 * FEATURE UNDER TEST
 * <p>
 * With FLEX access Raptor must support access paths with more than one leg. These access paths have
 * more transfers that regular paths, hence should not dominate access walking, but only get
 * accepted when they are better on time and/or cost.
 */
public class B10_FlexAccessTest implements RaptorTestConstants {

  private static final int TRANSFER_SLACK = 60;
  private static final int COST_ONE_STOP = RaptorCostConverter.toRaptorCost(2 * 60);
  private static final int COST_TRANSFER_SLACK = RaptorCostConverter.toRaptorCost(TRANSFER_SLACK);
  private static final int COST_ONE_SEC = RaptorCostConverter.toRaptorCost(1);

  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = new RaptorRequestBuilder<>();
  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  @BeforeEach
  void setup() {
    data.withRoute(
      route("R1", STOP_B, STOP_C, STOP_D, STOP_E, STOP_F)
        .withTimetable(schedule("0:10, 0:12, 0:14, 0:16, 0:20"))
    );
    // We will test board- and alight-slack in a separate test
    data.withSlackProvider(new DefaultSlackProvider(TRANSFER_SLACK, 0, 0));

    requestBuilder
      .searchParams()
      // All access paths are all pareto-optimal (McRaptor).
      .addAccessPaths(
        // lowest num-of-transfers (0)
        TestAccessEgress.walk(STOP_B, D10m, COST_ONE_STOP + COST_TRANSFER_SLACK),
        // lowest cost
        flexAndWalk(STOP_C, D2m, TWO_RIDES, 2 * COST_ONE_STOP - COST_ONE_SEC),
        // latest departure time
        flex(STOP_D, D3m, TWO_RIDES, 3 * COST_ONE_STOP),
        // best on combination of transfers and time
        flexAndWalk(STOP_E, D7m, ONE_RIDE, 4 * COST_ONE_STOP)
      )
      .addEgressPaths(TestAccessEgress.walk(STOP_F, D1m));

    requestBuilder.searchParams().earliestDepartureTime(T00_00).latestArrivalTime(T00_30);

    ModuleTestDebugLogging.setupDebugLogging(data, requestBuilder);
  }

  static List<RaptorModuleTestCase> testCases() {
    return RaptorModuleTestCase
      .of()
      .add(
        standard().not(TC_STANDARD_ONE),
        "Flex 3m 2x ~ D ~ BUS R1 0:14 0:20 ~ F ~ Walk 1m [0:10 0:21 11m 2tx]"
      )
      // First boarding wins with one-iteration
      .add(TC_STANDARD_ONE, "Walk 10m ~ B ~ BUS R1 0:10 0:20 ~ F ~ Walk 1m [0:00 0:21 21m 0tx]")
      .add(
        multiCriteria(),
        "Flex 3m 2x ~ D ~ BUS R1 0:14 0:20 ~ F ~ Walk 1m [0:10 0:21 11m 2tx $1500]", // ldt
        "Flex 2m 2x ~ C ~ BUS R1 0:12 0:20 ~ F ~ Walk 1m [0:09 0:21 12m 2tx $1499]", // cost
        "Flex 7m 1x ~ E ~ BUS R1 0:16 0:20 ~ F ~ Walk 1m [0:08 0:21 13m 1tx $1500]", // tx+time
        "Walk 10m ~ B ~ BUS R1 0:10 0:20 ~ F ~ Walk 1m [0:00 0:21 21m 0tx $1500]" // tx
      )
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testRaptor(RaptorModuleTestCase testCase) {
    var request = testCase.withConfig(requestBuilder);
    var response = raptorService.route(request, data);
    assertEquals(testCase.expected(), pathsToString(response));
  }
}
