/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ip.tcp.connection;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.context.SmartLifecycle;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.MessagingException;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.util.Assert;

/**
 * Base class for all connection factories.
 *
 * @author Gary Russell
 * @since 2.0
 *
 */
public abstract class AbstractConnectionFactory extends IntegrationObjectSupport
		implements ConnectionFactory, SmartLifecycle {

	protected static final int DEFAULT_REPLY_TIMEOUT = 10000;

	private volatile String host;

	private volatile int port;

	private volatile TcpListener listener;

	private volatile TcpSender sender;

	private volatile int soTimeout = -1;

	private volatile int soSendBufferSize;

	private volatile int soReceiveBufferSize;

	private volatile boolean soTcpNoDelay;

	private volatile int soLinger  = -1; // don't set by default

	private volatile boolean soKeepAlive;

	private volatile int soTrafficClass = -1; // don't set by default

	private volatile Executor taskExecutor;

	private volatile boolean privateExecutor;

	private volatile Deserializer<?> deserializer = new ByteArrayCrLfSerializer();

	private volatile Serializer<?> serializer = new ByteArrayCrLfSerializer();

	private volatile TcpMessageMapper mapper = new TcpMessageMapper();

	private volatile boolean singleUse;

	private volatile boolean active;

	private volatile TcpConnectionInterceptorFactoryChain interceptorFactoryChain;

	private volatile boolean lookupHost = true;

	private volatile List<TcpConnection> connections = new LinkedList<TcpConnection>();

	private volatile TcpSocketSupport tcpSocketSupport = new DefaultTcpSocketSupport();

	protected final Object lifecycleMonitor = new Object();

	private volatile long nextCheckForClosedNioConnections;

	private volatile int nioHarvestInterval = DEFAULT_NIO_HARVEST_INTERVAL;

	private static final int DEFAULT_NIO_HARVEST_INTERVAL = 2000;

	public AbstractConnectionFactory(int port) {
		this.port = port;
	}

	public AbstractConnectionFactory(String host, int port) {
		Assert.notNull(host, "host must not be null");
		this.host = host;
		this.port = port;
	}

	/**
	 * Sets socket attributes on the socket.
	 * @param socket The socket.
	 * @throws SocketException
	 */
	protected void setSocketAttributes(Socket socket) throws SocketException {
		if (this.soTimeout >= 0) {
			socket.setSoTimeout(this.soTimeout);
		}
		if (this.soSendBufferSize > 0) {
			socket.setSendBufferSize(this.soSendBufferSize);
		}
		if (this.soReceiveBufferSize > 0) {
			socket.setReceiveBufferSize(this.soReceiveBufferSize);
		}
		socket.setTcpNoDelay(this.soTcpNoDelay);
		if (this.soLinger >= 0) {
			socket.setSoLinger(true, this.soLinger);
		}
		if (this.soTrafficClass >= 0) {
			socket.setTrafficClass(this.soTrafficClass);
		}
		socket.setKeepAlive(this.soKeepAlive);
		this.tcpSocketSupport.postProcessSocket(socket);
	}

	/**
	 * @return the soTimeout
	 */
	public int getSoTimeout() {
		return soTimeout;
	}

	/**
	 * @param soTimeout the soTimeout to set
	 */
	public void setSoTimeout(int soTimeout) {
		this.soTimeout = soTimeout;
	}

	/**
	 * @return the soReceiveBufferSize
	 */
	public int getSoReceiveBufferSize() {
		return soReceiveBufferSize;
	}

	/**
	 * @param soReceiveBufferSize the soReceiveBufferSize to set
	 */
	public void setSoReceiveBufferSize(int soReceiveBufferSize) {
		this.soReceiveBufferSize = soReceiveBufferSize;
	}

	/**
	 * @return the soSendBufferSize
	 */
	public int getSoSendBufferSize() {
		return soSendBufferSize;
	}

	/**
	 * @param soSendBufferSize the soSendBufferSize to set
	 */
	public void setSoSendBufferSize(int soSendBufferSize) {
		this.soSendBufferSize = soSendBufferSize;
	}

	/**
	 * @return the soTcpNoDelay
	 */
	public boolean isSoTcpNoDelay() {
		return soTcpNoDelay;
	}

	/**
	 * @param soTcpNoDelay the soTcpNoDelay to set
	 */
	public void setSoTcpNoDelay(boolean soTcpNoDelay) {
		this.soTcpNoDelay = soTcpNoDelay;
	}

	/**
	 * @return the soLinger
	 */
	public int getSoLinger() {
		return soLinger;
	}

	/**
	 * @param soLinger the soLinger to set
	 */
	public void setSoLinger(int soLinger) {
		this.soLinger = soLinger;
	}

	/**
	 * @return the soKeepAlive
	 */
	public boolean isSoKeepAlive() {
		return soKeepAlive;
	}

	/**
	 * @param soKeepAlive the soKeepAlive to set
	 */
	public void setSoKeepAlive(boolean soKeepAlive) {
		this.soKeepAlive = soKeepAlive;
	}

	/**
	 * @return the soTrafficClass
	 */
	public int getSoTrafficClass() {
		return soTrafficClass;
	}

	/**
	 * @param soTrafficClass the soTrafficClass to set
	 */
	public void setSoTrafficClass(int soTrafficClass) {
		this.soTrafficClass = soTrafficClass;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @return the listener
	 */
	public TcpListener getListener() {
		return listener;
	}

	/**
	 * @return the sender
	 */
	public TcpSender getSender() {
		return sender;
	}

	/**
	 * @return the serializer
	 */
	public Serializer<?> getSerializer() {
		return serializer;
	}

	/**
	 * @return the deserializer
	 */
	public Deserializer<?> getDeserializer() {
		return deserializer;
	}

	/**
	 * @return the mapper
	 */
	public TcpMessageMapper getMapper() {
		return mapper;
	}

	/**
	 * @deprecated This property is no longer used. If you wish
	 * to use a fixed thread pool, provide your own Executor
	 * in {@link #setTaskExecutor(Executor)}.
	 * @return the poolSize
	 */
	@Deprecated
	public int getPoolSize() {
		return 0;
	}

	/**
	 * Registers a TcpListener to receive messages after
	 * the payload has been converted from the input data.
	 * @param listener the TcpListener.
	 */
	public void registerListener(TcpListener listener) {
		Assert.isNull(this.listener, this.getClass().getName() +
				" may only be used by one inbound adapter");
		this.listener = listener;
	}

	/**
	 * Registers a TcpSender; for server sockets, used to
	 * provide connection information so a sender can be used
	 * to reply to incoming messages.
	 * @param sender The sender
	 */
	public void registerSender(TcpSender sender) {
		Assert.isNull(this.sender, this.getClass().getName() +
				" may only be used by one outbound adapter");
		this.sender = sender;
	}

	/**
	 * @param taskExecutor the taskExecutor to set
	 */
	public void setTaskExecutor(Executor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 *
	 * @param deserializer the deserializer to set
	 */
	public void setDeserializer(Deserializer<?> deserializer) {
		this.deserializer = deserializer;
	}

	/**
	 *
	 * @param serializer the serializer to set
	 */
	public void setSerializer(Serializer<?> serializer) {
		this.serializer = serializer;
	}

	/**
	 *
	 * @param mapper the mapper to set; defaults to a {@link TcpMessageMapper}
	 */
	public void setMapper(TcpMessageMapper mapper) {
		this.mapper = mapper;
	}

	/**
	 * @return the singleUse
	 */
	public boolean isSingleUse() {
		return singleUse;
	}

	/**
	 * If true, sockets created by this factory will be used once.
	 * @param singleUse
	 */
	public void setSingleUse(boolean singleUse) {
		this.singleUse = singleUse;
	}


	/**
	 * @deprecated Default task executor is now a cached rather
	 * than a fixed pool executor. To use a pool, supply an
	 * appropriate Executor in {@link AbstractConnectionFactory#setTaskExecutor(Executor)}.
	 * Use {@link AbstractServerConnectionFactory#setBacklog(int)} to set the connection backlog.
	 */
	@Deprecated
	public void setPoolSize(int poolSize) {
	}

	public void setInterceptorFactoryChain(TcpConnectionInterceptorFactoryChain interceptorFactoryChain) {
		this.interceptorFactoryChain = interceptorFactoryChain;
	}

	/**
	 * If true, DNS reverse lookup is done on the remote ip address.
	 * Default true.
	 * @param lookupHost the lookupHost to set
	 */
	public void setLookupHost(boolean lookupHost) {
		this.lookupHost = lookupHost;
	}

	/**
	 * @return the lookupHost
	 */
	public boolean isLookupHost() {
		return lookupHost;
	}

	/**
	 * How often we clean up closed NIO connections if soTimeout is 0.
	 * Ignored when soTimeout > 0 because the clean up
	 * process is run as part of the timeout handling.
	 * Default 2000 milliseconds.
	 * @param nioHarvestInterval The interval in milliseconds.
	 */
	public void setNioHarvestInterval(int nioHarvestInterval) {
		Assert.isTrue(nioHarvestInterval > 0, "NIO Harvest interval must be > 0");
		this.nioHarvestInterval = nioHarvestInterval;
	}

	/**
	 * Closes the server.
	 */
	public abstract void close();

	public void start() {
		if (logger.isInfoEnabled()) {
			logger.info("started " + this);
		}
	}

	/**
	 * Creates a taskExecutor (if one was not provided).
	 */
	protected Executor getTaskExecutor() {
		if (!this.active) {
			throw new MessagingException("Connection Factory not started");
		}
		synchronized (this.lifecycleMonitor) {
			if (this.taskExecutor == null) {
				this.privateExecutor = true;
				this.taskExecutor = Executors.newCachedThreadPool();
			}
			return this.taskExecutor;
		}
	}

	/**
	 * Stops the server.
	 */
	public void stop() {
		this.active = false;
		this.close();
		synchronized (this.connections) {
			Iterator<TcpConnection> iterator = this.connections.iterator();
			while (iterator.hasNext()) {
				TcpConnection connection = iterator.next();
				connection.close();
				iterator.remove();
			}
		}
		synchronized (this.lifecycleMonitor) {
			if (this.privateExecutor) {
				ExecutorService executorService = (ExecutorService) this.taskExecutor;
				executorService.shutdown();
				try {
					if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
						logger.debug("Forcing executor shutdown");
						executorService.shutdownNow();
						if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
							logger.debug("Executor failed to shutdown");
						}
					}
				} catch (InterruptedException e) {
					executorService.shutdownNow();
					Thread.currentThread().interrupt();
				} finally {
					this.taskExecutor = null;
					this.privateExecutor = false;
				}
			}
		}
		if (logger.isInfoEnabled()) {
			logger.info("stopped " + this);
		}
	}

	protected TcpConnectionSupport wrapConnection(TcpConnectionSupport connection) throws Exception {
		try {
			if (this.interceptorFactoryChain == null) {
				return connection;
			}
			TcpConnectionInterceptorFactory[] interceptorFactories =
				this.interceptorFactoryChain.getInterceptorFactories();
			if (interceptorFactories == null) {
				return connection;
			}
			for (TcpConnectionInterceptorFactory factory : interceptorFactories) {
				TcpConnectionInterceptorSupport wrapper = factory.getInterceptor();
				wrapper.setTheConnection(connection);
				// if no ultimate listener or sender, register each wrapper in turn
				if (this.listener == null) {
					connection.registerListener(wrapper);
				}
				if (this.sender == null) {
					connection.registerSender(wrapper);
				}
				connection = wrapper;
			}
			return connection;
		} finally {
			this.addConnection(connection);
		}
	}

	/**
	 *
	 * Times out any expired connections then, if selectionCount > 0, processes the selected keys.
	 * Removes closed connections from the connections field, and from the connections parameter.
	 *
	 * @param selectionCount Number of IO Events, if 0 we were probably woken up by a close.
	 * @param selector The selector
	 * @param connections Map of connections
	 * @throws IOException
	 */
	protected void processNioSelections(int selectionCount, final Selector selector, ServerSocketChannel server,
			Map<SocketChannel, TcpNioConnection> connections) throws IOException {
		long now = System.currentTimeMillis();
		if (this.soTimeout > 0 ||
				now >= this.nextCheckForClosedNioConnections ||
				selectionCount == 0) {
			this.nextCheckForClosedNioConnections = now + this.nioHarvestInterval;
			Iterator<Entry<SocketChannel, TcpNioConnection>> it = connections.entrySet().iterator();
			while (it.hasNext()) {
				SocketChannel channel = it.next().getKey();
				if (!channel.isOpen()) {
					logger.debug("Removing closed channel");
					it.remove();
				}
				else if (soTimeout > 0) {
					TcpNioConnection connection = connections.get(channel);
					if (now - connection.getLastRead() >= this.soTimeout) {
						/*
						 * For client connections, we have to wait for 2 timeouts if the last
						 * send was within the current timeout.
						 */
						if (!connection.isServer() &&
							now - connection.getLastSend() < this.soTimeout &&
							now - connection.getLastRead() < this.soTimeout * 2)
						{
							if (logger.isDebugEnabled()) {
								logger.debug("Skipping a connection timeout because we have a recent send "
										+ connection.getConnectionId());
							}
						}
						else {
							if (logger.isWarnEnabled()) {
								logger.warn("Timing out TcpNioConnection " +
											this.port + " : " +
										    connection.getConnectionId());
							}
							connection.timeout();
						}
					}
				}
			}
		}
		this.harvestClosedConnections();
		if (logger.isTraceEnabled()) {
			if (host == null) {
				logger.trace("Port " + this.port + " SelectionCount: " + selectionCount);
			} else {
				logger.trace("Host " + this.host + " port " + this.port + " SelectionCount: " + selectionCount);
			}
		}
		if (selectionCount > 0) {
			Set<SelectionKey> keys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = keys.iterator();
			while (iterator.hasNext()) {
				final SelectionKey key = iterator.next();
				iterator.remove();
				try {
					if (!key.isValid()) {
						logger.debug("Selection key no longer valid");
					}
					else if (key.isReadable()) {
						key.interestOps(key.interestOps() - key.readyOps());
						final TcpNioConnection connection;
						connection = (TcpNioConnection) key.attachment();
						connection.setLastRead(System.currentTimeMillis());
						this.taskExecutor.execute(new Runnable() {
							public void run() {
								try {
									connection.readPacket();
								} catch (Exception e) {
									if (connection.isOpen()) {
										logger.error("Exception on read " +
												connection.getConnectionId() + " " +
												e.getMessage());
										connection.close();
									} else {
										logger.debug("Connection closed");
									}
								}
								if (key.channel().isOpen()) {
									key.interestOps(SelectionKey.OP_READ);
									selector.wakeup();
								}
							}});
					}
					else if (key.isAcceptable()) {
						try {
							doAccept(selector, server, now);
						} catch (Exception e) {
							logger.error("Exception accepting new connection", e);
						}
					}
					else {
						logger.error("Unexpected key: " + key);
					}
				} catch (CancelledKeyException e) {
					if (logger.isDebugEnabled()) {
						logger.debug("Selection key " + key + " cancelled");
					}
				} catch (Exception e) {
					logger.error("Exception on selection key " + key, e);
				}
			}
		}
	}

	/**
	 * @param selector
	 * @param now
	 * @throws IOException
	 */
	protected void doAccept(final Selector selector, ServerSocketChannel server, long now) throws IOException {
		throw new UnsupportedOperationException("Nio server factory must override this method");
	}

	public int getPhase() {
		return 0;
	}

	/**
	 * We are controlled by the startup options of
	 * the bound endpoint.
	 */
	public boolean isAutoStartup() {
		return false;
	}

	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	protected void addConnection(TcpConnection connection) {
		synchronized (this.connections) {
			if (!this.active) {
				connection.close();
				return;
			}
			this.connections.add(connection);
		}
	}

	protected void harvestClosedConnections() {
		synchronized (this.connections) {
			Iterator<TcpConnection> iterator = this.connections.iterator();
			while (iterator.hasNext()) {
				TcpConnection connection = iterator.next();
				if (!connection.isOpen()) {
					iterator.remove();
				}
			}
		}
	}

	public boolean isRunning() {
		return this.active;
	}

	/**
	 * @return the active
	 */
	protected boolean isActive() {
		return active;
	}

	/**
	 * @param active the active to set
	 */
	protected void setActive(boolean active) {
		this.active = active;
	}

	protected void checkActive() throws IOException {
		if (!this.isActive()) {
			throw new IOException(this + " connection factory has not been started");
		}
	}

	protected TcpSocketSupport getTcpSocketSupport() {
		return tcpSocketSupport;
	}

	public void setTcpSocketSupport(TcpSocketSupport tcpSocketSupport) {
		Assert.notNull(tcpSocketSupport, "TcpSocketSupport must not be null");
		this.tcpSocketSupport = tcpSocketSupport;
	}

}
