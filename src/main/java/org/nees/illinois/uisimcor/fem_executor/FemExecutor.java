package org.nees.illinois.uisimcor.fem_executor;

import java.io.IOException;

import org.nees.illinois.uisimcor.fem_executor.process.OutputFileParsingTask;
import org.nees.illinois.uisimcor.fem_executor.process.ProcessManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to execute FEM programs
 * 
 * @author Michael Bletzinger
 */
public class FemExecutor {
	/**
	 * Possible execution states
	 * 
	 */
	public enum ExecutionState {
		/**
		 * The command is executing.
		 */
		Executing,
		/**
		 * The command has finished executing.
		 */
		ExecutionFinished,
		/**
		 * We are done.
		 */
		Finished,
		/**
		 * We have not started executing yet.
		 */
		NotStarted,
		/**
		 * The output files are being parsed.
		 */
		ProcessingOutputFiles
	}

	/**
	 * Test function to see if the executor works in the given OS environment.
	 * 
	 * @param args
	 *            ignored.
	 */
	public static void main(final String[] args) {
		String os = System.getProperty("os.name");
		FemExecutor fem;
		if (os.contains("Window")) {
			fem = new FemExecutor("echo", "Is there anybody out there?", null);
		} else {
			fem = new FemExecutor("ls", "-l", null);
		}
		fem.startCmd();
		while (fem.isDone() == false) {
			try {
				final int waitTime = 500;
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				@SuppressWarnings("unused")
				int dumb = 0;
			}
		}
		System.out.print("Output: \"" + fem.getPm().getOutput() + "\"");
	}

	/**
	 * Line command
	 */
	private String command;

	/**
	 * Current execution state.
	 */
	private ExecutionState current = ExecutionState.NotStarted;

	/**
	 * default wait.
	 */
	private final int defaultWait = 2000;

	/**
	 * Filename as first argument.
	 */
	private String filename;
	/**
	 * Logger
	 **/
	private final Logger log = LoggerFactory.getLogger(FemExecutor.class);
	/**
	 * {@link ProcessManagement Wrapper} around command line executor.
	 */
	private ProcessManagement pm;
	/**
	 * Flag indicating that output files need to be parsed after execution.
	 */
	private boolean processOutputFiles = false;
	/**
	 * Wait interval for checking thread for done. Default is 2 seconds
	 */
	private int waitInMillisecs = defaultWait;

	/**
	 * Working directory for FEM execution
	 */
	private final String workDir;
	/**
	 * Output file {@link OutputFileParsingTask parser} for displacements.
	 */
	private final OutputFileParsingTask ofptDisp;
	/**
	 * Output file {@link OutputFileParsingTask parser} for forces.
	 */
	private final OutputFileParsingTask ofptForce;

	/**
	 * @param command
	 *            Line command
	 * @param filename
	 *            Filename as first argument.
	 * @param workDir
	 *            Working directory for execution. Use null if the current JVM
	 *            directory is fine.
	 */
	public FemExecutor(final String command, final String filename,
			final String workDir) {
		String sep = System.getProperty("file.separator");
		this.command = command;
		this.filename = filename;
		this.workDir = workDir;
		ofptDisp = new OutputFileParsingTask(workDir + sep + "tmp_disp.out");
		ofptForce = new OutputFileParsingTask(workDir + sep + "tmp_forc.out");
	}

	/**
	 * @return the command
	 */
	public final String getCommand() {
		return command;
	}

	/**
	 * @return the current
	 */
	public final ExecutionState getCurrent() {
		return current;
	}

	/**
	 * @return the defaultWait
	 */
	public final int getDefaultWait() {
		return defaultWait;
	}

	/**
	 * @return the filename
	 */
	public final String getFilename() {
		return filename;
	}

	/**
	 * @return the pm
	 */
	public final ProcessManagement getPm() {
		return pm;
	}

	/**
	 * @return the waitInMillisecs
	 */
	public final int getWaitInMillisecs() {
		return waitInMillisecs;
	}

	/**
	 * @return the workDir
	 */
	public final String getWorkDir() {
		return workDir;
	}

	/**
	 * @return the processOutputFiles
	 */
	public final boolean isProcessOutputFiles() {
		return processOutputFiles;
	}

	/**
	 * @param command
	 *            the command to set
	 */
	public final void setCommand(final String command) {
		this.command = command;
	}

	/**
	 * @param filename
	 *            the filename to set
	 */
	public final void setFilename(final String filename) {
		this.filename = filename;
	}

	/**
	 * @param processOutputFiles
	 *            the processOutputFiles to set
	 */
	public final void setProcessOutputFiles(final boolean processOutputFiles) {
		this.processOutputFiles = processOutputFiles;
	}

	/**
	 * @param waitInMillisecs
	 *            the waitInMillisecs to set
	 */
	public final void setWaitInMillisecs(final int waitInMillisecs) {
		this.waitInMillisecs = waitInMillisecs;
	}

	/**
	 * Create the {@link ProcessManagement ProcessManagement} instance and start
	 * it.
	 * 
	 * @return the {@link ProcessManagement ProcessManagement} instance
	 */
	public final ProcessManagement startCmd() {
		pm = new ProcessManagement(command, waitInMillisecs);
		pm.addArg(filename);
		pm.setWorkDir(workDir);
		try {
			pm.startExecute();
			current = ExecutionState.Executing;
			return pm;
		} catch (IOException e) {
			log.debug(command + " falied to start", e);
		}
		return null;
	}

	/**
	 * Execution Polling function. Use this repeatedly inside a polling loop to
	 * transition the process to new execution states.
	 * 
	 * @return True if the command has completed.
	 */
	public final boolean isDone() {
		boolean result = false;
		if (current.equals(ExecutionState.Executing)) {
			boolean done = pm.isDone();
			if (done) {
				current = ExecutionState.ExecutionFinished;
			}
		}
		if (current.equals(ExecutionState.ExecutionFinished)) {
			if (processOutputFiles) {
				Thread thrd1 = new Thread(ofptDisp);
				Thread thrd2 = new Thread(ofptForce);
				log.debug("Starting parsing threads");
				thrd1.start();
				thrd2.start();
				current = ExecutionState.ProcessingOutputFiles;
			} else {
				current = ExecutionState.Finished;
			}
		}
		if (current.equals(ExecutionState.ProcessingOutputFiles)) {
			boolean done = ofptDisp.isDone() && ofptForce.isDone();
			if (done) {
				current = ExecutionState.Finished;
			}
		}
		if (current.equals(ExecutionState.Finished)) {
			result = true;
		}
		log.debug("Current state is " + current);
		return result;
	}
	/**
	 * Return the displacements data set.
	 *@return
	 *double matrix
	 */
	public final double [][] getDisplacements() {
		return ofptDisp.getData();
	}
	/**
	 * Return the forces data set.
	 *@return
	 *double matrix
	 */
	public final double [][] getForces() {
		return ofptForce.getData();
	}
}