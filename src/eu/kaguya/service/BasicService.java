package eu.kaguya.service;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.kaguya.dto.Currency;
import eu.kaguya.service.remote.RemoteService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
@Service
public class BasicService implements UncaughtExceptionHandler {

	// for every execution - one factory
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	@Bean(name = "conciseThreadFactory")
	public ThreadFactory conciseThreadFactory() {
		log.info("Making ThreadFactory");
		return new ThreadFactoryBuilder().setDaemon(true).setUncaughtExceptionHandler(this).build();
	}

	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	@Bean(name = "closeableOKHTTP")
	public OkHttpClient closeableOKHTTP() {
		log.info("Making OkHttpClient");
		ConnectionPool pool = new ConnectionPool(5, 10, TimeUnit.MINUTES);
		OkHttpClient client = new OkHttpClient.Builder() //
				.connectTimeout(3, TimeUnit.MINUTES) //
				.followRedirects(true) //
				.readTimeout(3, TimeUnit.MINUTES) //
				.retryOnConnectionFailure(false) //
				.writeTimeout(3, TimeUnit.MINUTES). //
				connectionPool(pool) //
				.build();
		return client;
	}

	// executor that will let Tomcat to shut down gracefully
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	@Bean(name = "daemonExecutor")
	public Executor daemonExecutor(ThreadFactory conciseThreadFactory) {
		log.info("Making Executor");
		ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 3, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(50, true),
				conciseThreadFactory, new ThreadPoolExecutor.DiscardPolicy());
		threadPoolExecutor.allowCoreThreadTimeOut(true);
		threadPoolExecutor.prestartAllCoreThreads();
		return threadPoolExecutor;
	}
	
	public void uncaughtException(Thread t, Throwable e) {
		log.warn("[\u001b[31mEXCEPTION\u001b[m] in [{}] {}::{}", t.getName(), e.getClass().getSimpleName(), e.getMessage());
	}
	
	@PreDestroy
	public void destroy() {
		if (daemonExecutor != null && daemonExecutor instanceof ThreadPoolExecutor) {
			ThreadPoolExecutor tpe = (ThreadPoolExecutor) daemonExecutor;
			tpe.shutdown();
		}
		if (okHttpClient != null) {
			ConnectionPool connectionPool = okHttpClient.connectionPool();
			connectionPool.evictAll();
			log.info("OKHTTP connections iddle: {}, all: {}", connectionPool.idleConnectionCount(), connectionPool.connectionCount());
			ExecutorService executorService = okHttpClient.dispatcher().executorService();
			executorService.shutdown();
			try {
				executorService.awaitTermination(3, TimeUnit.MINUTES);
				log.info("OKHTTP ExecutorService closed.");
			} catch (InterruptedException e) {
				log.warn("InterruptedException on destroy()", e);
			}
		}
	}
	
	public static final String API = "http://api.fixer.io/";

	@Resource(name = "daemonExecutor")
	Executor daemonExecutor;
	@Resource(name = "closeableOKHTTP")
	OkHttpClient okHttpClient;
	@Autowired
	RemoteService remoteService;

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	public RemoteService getRemote() {
		Gson gson = new GsonBuilder().create();
		RemoteService remoteService = new Retrofit.Builder() //
				.baseUrl(API) //
				.client(okHttpClient) //
				.addConverterFactory(GsonConverterFactory.create(gson)) //
				.callbackExecutor(daemonExecutor) //
				.build() //
				.create(RemoteService.class);
		return remoteService;
	}

	public Currency test() throws IOException {
		return remoteService.latest("USD,GBP", "EUR").execute().body();
	}
	
}
