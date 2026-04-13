package com.sprinkleclaw.workflow.orchestration.router;

import com.sprinkleclaw.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * 路由编排：根据路由函数的返回值选择对应分支执行。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class RouterWorkflow<I, O> implements Workflow<I, O> {

    private final Function<I, String> routeFunction;
    private final Map<String, WorkflowStep<I, O>> routes;
    private final WorkflowStep<I, O> defaultRoute;

    public RouterWorkflow(Function<I, String> routeFunction,
                          Map<String, WorkflowStep<I, O>> routes,
                          WorkflowStep<I, O> defaultRoute) {
        this.routeFunction = routeFunction;
        this.routes = Map.copyOf(routes);
        this.defaultRoute = defaultRoute;
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "router");
        var start = Instant.now();

        String routeKey = routeFunction.apply(input);
        WorkflowStep<I, O> chosen = routes.getOrDefault(routeKey, defaultRoute);
        if (chosen == null) {
            var ex = new WorkflowException("router", -1,
                    "No route found for key '" + routeKey + "' and no default route configured");
            return WorkflowResult.failure(ex, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        }

        var stepStart = Instant.now();
        try {
            O output = chosen.execute(input, ctx);
            ctx.recordStep(StepResult.success(
                    chosen.name(), output,
                    Duration.between(stepStart, Instant.now()), stepStart));
            return WorkflowResult.success(output, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        } catch (Exception e) {
            ctx.recordStep(StepResult.failure(
                    chosen.name(), e,
                    Duration.between(stepStart, Instant.now()), stepStart));
            var ex = new WorkflowException(chosen.name(), 0, e);
            return WorkflowResult.failure(ex, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        }
    }

    @Override
    public String name() { return "router"; }
}
