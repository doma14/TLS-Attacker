/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0 http://www.apache.org/licenses/LICENSE-2.0
 */
package tlsattacker.fuzzer.executor;

import tlsattacker.fuzzer.mutator.Mutator;
import tlsattacker.fuzzer.agents.AgentFactory;
import tlsattacker.fuzzer.agents.Agent;
import tlsattacker.fuzzer.config.EvolutionaryFuzzerConfig;
import de.rub.nds.tlsattacker.tls.config.WorkflowTraceSerializer;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowTrace;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import tlsattacker.fuzzer.server.ServerManager;
import tlsattacker.fuzzer.server.TLSServer;
import tlsattacker.fuzzer.testvector.TestVector;
import tlsattacker.fuzzer.testvector.TestVectorSerializer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.rub.nds.tlsattacker.tls.config.ClientCommandConfig;
import de.rub.nds.tlsattacker.tls.config.CommandConfig;
import de.rub.nds.tlsattacker.tls.constants.CipherSuite;
import de.rub.nds.tlsattacker.tls.constants.ConnectionEnd;
import de.rub.nds.tlsattacker.tls.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.tls.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.workflow.DHWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.DtlsDhWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.DtlsEcdhWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.DtlsRsaWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.ECDHWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.RsaWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.UnsupportedWorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.action.TLSAction;
import de.rub.nds.tlsattacker.tls.workflow.action.executor.ExecutorType;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This ThreadPool manages the Threads for the different Executors and is
 * responsible for the continious exectution of new Fuzzingvectors.
 * 
 * @author Robert Merget - robert.merget@rub.de
 */
public class ExecutorThreadPool implements Runnable {

    private static final Logger LOG = Logger.getLogger(ExecutorThreadPool.class.getName());

    // Number of Threads which execute FuzzingVectors
    private final int poolSize;
    //
    private final ThreadPoolExecutor executor;
    // The Mutator used by the ExecutorPool to fetch new Tasks
    private final Mutator mutator;
    // The Executor thread pool will continuasly fetch and execute new Tasks
    // while this is false
    private boolean stopped = true;
    // Counts the number of executed Tasks for statisticall purposes.
    private long runs = 0;
    // List of Workflowtraces that should be executed before we start generating
    // new Workflows
    private final List<TestVector> list;
    // The Config the ExecutorThreadPool uses
    private final EvolutionaryFuzzerConfig config;

    /**
     * Constructor for the ExecutorThreadPool
     * 
     * @param poolSize
     *            Number of Threads the pool Manages
     * @param mutator
     *            Mutator which is used for the Generation of new
     *            FuzzingVectors.
     */
    public ExecutorThreadPool(int poolSize, Mutator mutator, EvolutionaryFuzzerConfig config) {
	this.config = config;
	this.poolSize = poolSize;
	this.mutator = mutator;
	BlockingQueue workQueue = new ArrayBlockingQueue<>(poolSize * 5);

	ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Executor-%d").setDaemon(false).build();

	executor = new BlockingThreadPoolExecutor(poolSize, poolSize, config.getTimeout(), TimeUnit.MICROSECONDS,
		workQueue, threadFactory);

	LOG.log(Level.INFO, "Reading Archive Vectors in:");

	File f = new File(config.getArchiveFolder());
	list = TestVectorSerializer.readFolder(f);
	LOG.log(Level.INFO, "Loaded Archive Vectors:{0}", list.size());
	if (list.isEmpty()) {
	    LOG.log(Level.INFO, "Creating Fuzzer Seed:");
	    list.addAll(generateSeed());
	}
	// We need to fix Server responses before we can use the workflowtraces
	// for mutation
	LOG.log(Level.INFO, "Preparing Vectors:{0}", list.size());
	for (TestVector vector : list) {
	    vector.getTrace().makeGeneric();
	}
    }

    private List<TestVector> generateSeed() {
	List<TestVector> newList = new LinkedList<TestVector>();
	List<WorkflowConfigurationFactory> factoryList = new LinkedList<>();
	factoryList.add(new DHWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new DtlsDhWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new DtlsEcdhWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new DtlsRsaWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new ECDHWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new RsaWorkflowConfigurationFactory(new ClientCommandConfig()));
	factoryList.add(new UnsupportedWorkflowConfigurationFactory(new ClientCommandConfig()));
	for (WorkflowConfigurationFactory factory : factoryList) {
	    WorkflowTrace trace = factory.createClientHelloTlsContext(ConnectionEnd.CLIENT).getWorkflowTrace();
	    newList.add(new TestVector(trace, mutator.getCertMutator().getServerCertificateStructure(), mutator
		    .getCertMutator().getClientCertificateStructure(), ExecutorType.TLS, null));
	    trace = factory.createFullServerResponseTlsContext(ConnectionEnd.CLIENT).getWorkflowTrace();
	    newList.add(new TestVector(trace, mutator.getCertMutator().getServerCertificateStructure(), mutator
		    .getCertMutator().getClientCertificateStructure(), ExecutorType.TLS, null));
	    trace = factory.createFullTlsContext(ConnectionEnd.CLIENT).getWorkflowTrace();
	    newList.add(new TestVector(trace, mutator.getCertMutator().getServerCertificateStructure(), mutator
		    .getCertMutator().getClientCertificateStructure(), ExecutorType.TLS, null));
	    trace = factory.createHandshakeTlsContext(ConnectionEnd.CLIENT).getWorkflowTrace();
	    newList.add(new TestVector(trace, mutator.getCertMutator().getServerCertificateStructure(), mutator
		    .getCertMutator().getClientCertificateStructure(), ExecutorType.TLS, null));
	}
	for (TestVector vector : newList) {
	    for (ProtocolMessage pm : vector.getTrace().getActuallySentHandshakeMessagesOfType(
		    HandshakeMessageType.CLIENT_HELLO)) {
		List<CipherSuite> suiteList = new LinkedList<>();
		suiteList.add(CipherSuite.getRandom());
		((ClientHelloMessage) pm).setSupportedCipherSuites(suiteList);
	    }
	}
	return newList;
    }

    /**
     * Returns the Number of executed FuzzingVectors
     * 
     * @return Number of executed FuzzingVectors
     */
    public long getRuns() {
	return runs;
    }

    /**
     * Starts the Thread which manages the other Threads
     */
    @Override
    public void run() {
	stopped = false;
	// Dont save old results
	config.setSerialize(false);
	if (!config.isNoOld()) {
	    for (int i = 0; i < list.size(); i++) {

		TLSServer server = null;
		try {
		    if (!stopped) {
			server = ServerManager.getInstance().getFreeServer();
			TestVector vector = list.get(i);
			vector.getTrace().reset();
			vector.getTrace().makeGeneric();

			Agent agent = AgentFactory.generateAgent(config, vector.getServerKeyCert());
			Runnable worker = new TLSExecutor(vector, server, agent);
			executor.submit(worker);
			runs++;

		    } else {
			try {
			    Thread.sleep(1000);
			} catch (InterruptedException ex) {
			    Logger.getLogger(ExecutorThreadPool.class.getName()).log(Level.SEVERE,
				    "Thread interruiped while the ThreadPool is paused.", ex);
			}
		    }
		} catch (Throwable ex) {
		    LOG.log(Level.WARNING, "Exception encountered with TestVector", ex);
		    if (server != null) {
			server.release();
		    }
		}
	    }

	    // Save new results
	    config.setSerialize(true);
	    while (true) {
		TLSServer server = null;
		try {
		    if (!stopped) {
			server = ServerManager.getInstance().getFreeServer();
			TestVector vector = mutator.getNewMutation();
			Agent agent = AgentFactory.generateAgent(config, vector.getServerKeyCert());
			Runnable worker = new TLSExecutor(vector, server, agent);
			executor.submit(worker);
			runs++;

		    } else {
			try {
			    Thread.sleep(1000);
			} catch (InterruptedException ex) {
			    Logger.getLogger(ExecutorThreadPool.class.getName()).log(Level.SEVERE,
				    "Thread interruiped while the ThreadPool is paused.", ex);
			}
		    }
		} catch (Throwable ex) {
		    LOG.log(Level.WARNING, "Exception encountered with TestVector", ex);
		    if (server != null) {
			server.release();
		    }
		}

	    }
	    /*
	     * executor.shutdown(); while (!executor.isTerminated()) { }
	     * System.out.println('ExecutorThread Pool Shutdown');
	     */
	}
    }

    /**
     * Returns if the ThreadPool is currently stopped.
     * 
     * @return if the ThreadPool is currently stopped
     */
    public synchronized boolean isStopped() {
	return stopped;
    }

    /**
     * Starts or stops the Threadpool
     * 
     * @param stopped
     */
    public synchronized void setStopped(boolean stopped) {
	this.stopped = stopped;
    }

    public synchronized boolean hasRunningThreads() {
	return executor.getActiveCount() == 0;
    }
}
