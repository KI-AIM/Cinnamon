package de.kiaim.cinnamon.test.platform;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;

/**
 * @author Daniel Preciado-Marquez
 */
@TestConfiguration
public class TestTaskSchedulerConfig {

	/**
	 * Custom task scheduler bean that executes the task synchronously.
	 *
	 * @return The TaskScheduler bean.
	 */
	@Bean
	@Primary
	public TaskScheduler taskScheduler() {
		TaskScheduler mockScheduler = Mockito.mock(TaskScheduler.class);

		// Create an Answer that executes the Runnable immediately
		Answer<ScheduledFuture<?>> executeImmediately = invocation -> {
			Runnable runnable = invocation.getArgument(0);
			runnable.run();
			return null;
		};

		// Mock all the schedule methods
		Mockito.doAnswer(executeImmediately).when(mockScheduler).schedule(any(Runnable.class), any(Instant.class));
		Mockito.doAnswer(executeImmediately).when(mockScheduler).schedule(any(Runnable.class), any(Date.class));
		Mockito.doAnswer(executeImmediately).when(mockScheduler).schedule(any(Runnable.class), any(Trigger.class));

		return mockScheduler;
	}
}
