package io.dropwizard.lifecycle.setup;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.lifecycle.JettyManaged;
import io.dropwizard.lifecycle.Managed;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LifecycleEnvironmentTest {

    private final LifecycleEnvironment environment = new LifecycleEnvironment(new MetricRegistry());

    @Test
    void managesLifeCycleObjects() {
        final LifeCycle lifeCycle = mock(LifeCycle.class);
        environment.manage(lifeCycle);

        final ContainerLifeCycle container = new ContainerLifeCycle();
        environment.attach(container);

        assertThat(container.getBeans())
            .contains(lifeCycle);
    }

    @Test
    void managesManagedObjects() {
        final Managed managed = mock(Managed.class);
        environment.manage(managed);

        final ContainerLifeCycle container = new ContainerLifeCycle();
        environment.attach(container);

        assertThat(container.getBeans())
            .singleElement()
            .isInstanceOfSatisfying(JettyManaged.class, jettyManaged ->
                assertThat(jettyManaged.getManaged()).isEqualTo(managed));
    }

    @Test
    void scheduledExecutorServiceBuildsDaemonThreads() throws ExecutionException, InterruptedException {
        final ScheduledExecutorService executorService = environment.scheduledExecutorService("daemon-%d", true).build();
        final Future<Boolean> isDaemon = executorService.submit(() -> Thread.currentThread().isDaemon());

        assertThat(isDaemon.get()).isTrue();
    }

    @Test
    void scheduledExecutorServiceBuildsUserThreadsByDefault() throws ExecutionException, InterruptedException {
        final ScheduledExecutorService executorService = environment.scheduledExecutorService("user-%d").build();
        final Future<Boolean> isDaemon = executorService.submit(() -> Thread.currentThread().isDaemon());

        assertThat(isDaemon.get()).isFalse();
    }

    @Test
    void scheduledExecutorServiceThreadFactory() throws ExecutionException, InterruptedException {
        final String expectedName = "DropWizard ThreadFactory Test";
        final String expectedNamePattern = expectedName + "-%d";

        final ThreadFactory tfactory = buildThreadFactory(expectedNamePattern);

        final ScheduledExecutorService executorService = environment.scheduledExecutorService("DropWizard Service", tfactory).build();
        final Future<Boolean> isFactoryInUse = executorService.submit(() -> Thread.currentThread().getName().startsWith(expectedName));

        assertThat(isFactoryInUse.get()).isTrue();
    }

    @Test
    void executorServiceThreadFactory() throws ExecutionException, InterruptedException {
        final String expectedName = "DropWizard ThreadFactory Test";
        final String expectedNamePattern = expectedName + "-%d";

        final ThreadFactory tfactory = buildThreadFactory(expectedNamePattern);

        final ExecutorService executorService = environment.executorService("Dropwizard Service", tfactory).build();
        final Future<Boolean> isFactoryInUse = executorService.submit(() -> Thread.currentThread().getName().startsWith(expectedName));

        assertThat(isFactoryInUse.get()).isTrue();
    }

    private ThreadFactory buildThreadFactory(String expectedNamePattern) {
        return new ThreadFactory() {
            final AtomicLong counter = new AtomicLong(0L);
            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setDaemon(false);
                thread.setName(String.format(expectedNamePattern, counter.incrementAndGet()));
                return thread;
            }
        };
    }
}
