package io.korti.tracebullet.threading;

import io.korti.tracebullet.TraceBullet;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.IntSupplier;

@ParametersAreNonnullByDefault
public class ThreadPoolManager {

	private final Map<ThreadPool, ExecutorService> executorServiceMap = new ConcurrentHashMap<>();
	private final Map<ThreadPool, ScheduledExecutorService> scheduledExecutorServiceMap = new ConcurrentHashMap<>();

	public ThreadPoolManager() {

	}

	public void registerThreadPool(ThreadPool threadPool, IntSupplier threadPoolSize) {
		ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize.getAsInt(), createThreadFactory(threadPool));
		registerThreadPool(threadPool, executorService);
	}

	public void registerThreadPool(ThreadPool threadPool, ExecutorService executorService) {
		if (executorServiceMap.containsKey(threadPool)) {
			throw new IllegalStateException("There is already a thread pool registered under %s.".formatted(threadPool));
		}

		executorServiceMap.put(threadPool, executorService);
	}

	public ExecutorService getExecutorService(ThreadPool threadPool) {
		if (!executorServiceMap.containsKey(threadPool)) {
			throw new IllegalStateException("No thread pool registered under %s.".formatted(threadPool));
		}
		return executorServiceMap.get(threadPool);
	}

	public void registerScheduledThreadPool(ThreadPool threadPool, IntSupplier threadPoolSize) {
		ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(threadPoolSize.getAsInt(), createThreadFactory(threadPool));
		registerScheduledThreadPool(threadPool, scheduledExecutorService);
	}

	public void registerScheduledThreadPool(ThreadPool threadPool, ScheduledExecutorService scheduledExecutorService) {
		if (scheduledExecutorServiceMap.containsKey(threadPool)) {
			throw new IllegalStateException("There is already a thread pool registered under %s".formatted(threadPool));
		}

		scheduledExecutorServiceMap.put(threadPool, scheduledExecutorService);
	}

	public ScheduledExecutorService getScheduledExecutorService(ThreadPool threadPool) {
		if (!scheduledExecutorServiceMap.containsKey(threadPool)) {
			throw new IllegalStateException("No thread pool registered under %s.".formatted(threadPool));
		}
		return scheduledExecutorServiceMap.get(threadPool);
	}

	private static ThreadFactory createThreadFactory(ThreadPool threadPool) {
		return BasicThreadFactory.builder()
				.namingPattern("%s-%s-%%d".formatted(TraceBullet.MODID, threadPool.getName()))
				.priority(Thread.NORM_PRIORITY).build();
	}

}
