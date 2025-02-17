package org.opentripplanner.raptor.rangeraptor.path.configure;

import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorStandard;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorStandardAndLatestDepature;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithCost;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithCostAndLatestDeparture;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithRelaxedCost;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithRelaxedCostAndLatestDeparture;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithTimetable;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithTimetableAndCost;
import static org.opentripplanner.raptor.rangeraptor.path.PathParetoSetComparators.comparatorWithTimetableAndRelaxedCost;

import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.model.SearchDirection;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.path.RaptorStopNameResolver;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.rangeraptor.context.SearchContext;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerLifeCycle;
import org.opentripplanner.raptor.rangeraptor.path.DestinationArrivalPaths;
import org.opentripplanner.raptor.rangeraptor.path.ForwardPathMapper;
import org.opentripplanner.raptor.rangeraptor.path.PathMapper;
import org.opentripplanner.raptor.rangeraptor.path.ReversePathMapper;
import org.opentripplanner.raptor.spi.CostCalculator;
import org.opentripplanner.raptor.spi.RaptorPathConstrainedTransferSearch;
import org.opentripplanner.raptor.spi.RaptorSlackProvider;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;

/**
 * This class is responsible for creating a a result collector - the set of paths.
 * <p/>
 * This class have REQUEST scope, so a new instance should be created for each new request/travel
 * search.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public class PathConfig<T extends RaptorTripSchedule> {

  private final SearchContext<T> ctx;

  public PathConfig(SearchContext<T> context) {
    this.ctx = context;
  }

  /**
   * Create a new {@link DestinationArrivalPaths} each time it is invoked. The given {@code
   * includeCost} decide if the cost should be included in the pareto set criteria or not.
   */
  public DestinationArrivalPaths<T> createDestArrivalPaths(boolean includeCost) {
    return new DestinationArrivalPaths<>(
      paretoComparator(includeCost),
      ctx.calculator(),
      ctx.costCalculator(),
      ctx.slackProvider(),
      createPathMapper(),
      ctx.debugFactory(),
      ctx.stopNameResolver(),
      ctx.lifeCycle()
    );
  }

  private ParetoComparator<RaptorPath<T>> paretoComparator(boolean includeCost) {
    boolean includeRelaxedCost =
      includeCost && ctx.searchParams().relaxCostAtDestination().isPresent();
    boolean includeTimetable = ctx.searchParams().timetable();
    boolean preferLateArrival = ctx.searchParams().preferLateArrival();

    if (includeRelaxedCost) {
      double relaxedCost = ctx.searchParams().relaxCostAtDestination().get();

      if (includeTimetable) {
        return comparatorWithTimetableAndRelaxedCost(relaxedCost);
      }
      if (preferLateArrival) {
        return comparatorWithRelaxedCostAndLatestDeparture(relaxedCost);
      }
      return comparatorWithRelaxedCost(relaxedCost);
    }

    if (includeCost) {
      if (includeTimetable) {
        return comparatorWithTimetableAndCost();
      }
      if (preferLateArrival) {
        return comparatorWithCostAndLatestDeparture();
      }
      return comparatorWithCost();
    }

    if (includeTimetable) {
      return comparatorWithTimetable();
    }
    if (preferLateArrival) {
      return comparatorStandardAndLatestDepature();
    }
    return comparatorStandard();
  }

  private PathMapper<T> createPathMapper() {
    return createPathMapper(
      ctx.transit().transferConstraintsSearch(),
      ctx.costCalculator(),
      ctx.stopNameResolver(),
      ctx.lifeCycle(),
      ctx.searchDirection(),
      ctx.profile(),
      ctx.raptorSlackProvider()
    );
  }

  private static <S extends RaptorTripSchedule> PathMapper<S> createPathMapper(
    RaptorPathConstrainedTransferSearch<S> txConstraintsSearch,
    CostCalculator<S> costCalculator,
    RaptorStopNameResolver stopNameResolver,
    WorkerLifeCycle lifeCycle,
    SearchDirection searchDirection,
    RaptorProfile profile,
    RaptorSlackProvider slackProvider
  ) {
    return searchDirection.isForward()
      ? new ForwardPathMapper<S>(
        txConstraintsSearch,
        slackProvider,
        costCalculator,
        stopNameResolver,
        lifeCycle,
        profile.useApproximateTripSearch()
      )
      : new ReversePathMapper<S>(
        txConstraintsSearch,
        slackProvider,
        costCalculator,
        stopNameResolver,
        lifeCycle,
        profile.useApproximateTripSearch()
      );
  }
}
