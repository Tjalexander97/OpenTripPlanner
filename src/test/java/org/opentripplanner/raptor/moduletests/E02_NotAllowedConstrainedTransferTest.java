package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_STANDARD_REV;
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

/**
 * FEATURE UNDER TEST
 * <p>
 * Raptor should NOT return a path with a NOT-ALLOWED transfer, instead it should try to find
 * another option.
 */
public class E02_NotAllowedConstrainedTransferTest implements RaptorTestConstants {

  private static final String EXP_PATH =
    "Walk 30s ~ A ~ BUS R1 0:02 0:05 ~ B " +
    "~ BUS R3 0:15 0:20 ~ C ~ Walk 30s [0:01:30 0:20:30 19m 1tx";
  private static final String EXP_PATH_NO_COST = EXP_PATH + "]";
  private static final String EXP_PATH_WITH_COST = EXP_PATH + " $2500]";
  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = new RaptorRequestBuilder<>();
  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  /**
   * Schedule: Stop:   1       2       3 R1: 00:02 - 00:05 R2:         00:05 - 00:10
   * <p>
   * Access(stop 1) and egress(stop 3) is 30s.
   */
  @BeforeEach
  public void setup() {
    var r1 = route("R1", STOP_A, STOP_B).withTimetable(schedule("0:02 0:05"));
    var r2 = route("R2", STOP_B, STOP_C)
      .withTimetable(
        schedule("0:10 0:15"),
        // Add another schedule - should not be used even if the not-allowed is
        // attached to the first one - not-allowed in Raptor apply to the Route.
        // The trip/timetable search should handle not-allowed on trip level.
        schedule("0:12 0:17")
      );
    var r3 = route("R3", STOP_B, STOP_C).withTimetable(schedule("0:15 0:20"));

    var tripR1a = r1.timetable().getTripSchedule(0);
    var tripR2a = r2.timetable().getTripSchedule(0);

    data.withRoutes(r1, r2, r3);

    // Apply not-allowed on the first trip from R1 and R2 - when found this will apply to
    // the second trip in R2 as well. This is of cause not a correct way to implement the
    // transit model, a not-allowed transfer should apply to ALL trips if constraint is passed
    // to raptor.
    data.withConstrainedTransfer(tripR1a, STOP_B, tripR2a, STOP_B, TestTransitData.TX_NOT_ALLOWED);
    data.mcCostParamsBuilder().transferCost(100);

    // NOTE! No search-window set
    requestBuilder
      .searchParams()
      .constrainedTransfers(true)
      .addAccessPaths(TestAccessEgress.walk(STOP_A, D30s))
      .addEgressPaths(TestAccessEgress.walk(STOP_C, D30s))
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T00_30)
      .timetable(true);

    ModuleTestDebugLogging.setupDebugLogging(data, requestBuilder);
  }

  static List<RaptorModuleTestCase> testCases() {
    // TODO - This is a bug - the reverse search should not allow the forbidden boarding
    //      - TC_STANDARD_REV is excluded since the test fails
    return RaptorModuleTestCase
      .of()
      .add(standard().not(TC_STANDARD_REV), EXP_PATH_NO_COST)
      .add(multiCriteria(), EXP_PATH_WITH_COST)
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
