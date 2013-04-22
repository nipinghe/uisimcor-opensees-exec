package org.nees.illinois.uisimcor.fem_executor.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.nees.illinois.uisimcor.fem_executor.FemExecutorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to load and save FEM Executor configuration files stored as Java
 * Properties.
 * 
 * @author Michael Bletzinger
 */
public class LoadSaveConfig {
	/**
	 * @return the femConfig
	 */
	public final FemExecutorConfig getFemConfig() {
		return femConfig;
	}

	/**
	 * @param femConfig
	 *            the femConfig to set
	 */
	public final void setFemConfig(FemExecutorConfig femConfig) {
		this.femConfig = femConfig;
	}

	/**
	 * FEM Executor Configuration
	 */
	private FemExecutorConfig femConfig;
	/**
	 * Path to configuration file.
	 */
	private String configFilePath;

	/**
	 * Encoder/Decoder for displacement DOF lists.
	 */
	private final EncodeDecodeList<DispDof, DispDofDecoder> eoDispDofList = new EncodeDecodeList<DispDof, DispDofDecoder>(
			new DispDofDecoder());

	/**
	 * Encoder/Decoder for displacement integer lists.
	 */
	private final EncodeDecodeList<Integer, IntegerDecoder> eoIntegerList = new EncodeDecodeList<Integer, IntegerDecoder>(
			new IntegerDecoder());
	/**
	 * Logger.
	 **/
	private final Logger log = LoggerFactory.getLogger(LoadSaveConfig.class);
	/**
	 * Properties for storing configuration data for a substructure.
	 */
	private Properties props;

	/**
	 * @return the configFilePath
	 */
	public final String getConfigFilePath() {
		return configFilePath;
	}

	/**
	 * Load a configuration.
	 */
	public final void load() {
		File configFile = new File(configFilePath);
		if (configFile.canRead() == false) {
			log.error("Unable to read from \"" + configFilePath + "\"");
			return;
		}
		props = new Properties();

		InputStream configI;
		try {
			configI = new FileInputStream(configFile);
			props.load(configI);
		} catch (Exception e) {
			log.error(
					"Unable to read from \"" + configFilePath + "\" because ",
					e);
			return;
		}
		String workDir = props.getProperty("work.dir");
		femConfig = new FemExecutorConfig(workDir);
		for(FemProgram p : FemProgram.values()) {
			FemProgramConfig fProgCfg = loadFemProgram(p);
			if(fProgCfg == null) {
				continue;
			}
			femConfig.getFemProgramParameters().put(p, fProgCfg);
		}
		String str = props.getProperty("substructures");
		String[] names = str.split(", ");
		for (String n : names) {
			FemConfig cfg = loadSubStructure(n);
			femConfig.getSubstructCfgs().put(n, cfg);
		}
	}

	private void saveFemProgram(FemProgramConfig progCfg) {
		FemProgram ptype = progCfg.getProgram();
		props.put(ptype + ".executable", progCfg.getExecutablePath());
		props.put(ptype + ".static.script",
				progCfg.getStaticAnalysisScriptPath());
	}
	private FemProgramConfig loadFemProgram(FemProgram ptype) {
		String executable = props.getProperty(ptype + ".executable");
		if(executable == null) {
			return null;
		}
		String staticScript = props.getProperty(ptype + ".static.script");
		FemProgramConfig result = new FemProgramConfig(ptype, executable, staticScript);
		return result;
	}

	/**
	 * Load a configuration for a substructure.
	 * 
	 * @param name
	 *            Name of substructure.
	 * @return Configuration data.
	 */
	private FemConfig loadSubStructure(final String name) {
		String address = name;
		String str = props.getProperty(name + ".dimension");
		DimensionType dim = null;
		if (str == null) {
			log.error("Dimension not found for " + name);
		} else {
			try {
				dim = DimensionType.valueOf(str);
			} catch (Exception e) {
				log.error("Dimension \"" + str + "\" not recognized for "
						+ name);
			}
		}
		str = props.getProperty(name + ".control.nodes");
		List<Integer> nodes = null;
		if (str == null) {
			log.error("Control nodes not found for " + name);
		} else {
			try {
				nodes = eoIntegerList.parse(str);
			} catch (Exception e) {
				log.error("Control node list \"" + str
						+ "\" not recognized for " + name, e);
			}
		}
		str = props.getProperty(name + ".fem.program");
		FemProgram fem = null;
		if (str == null) {
			log.error("FEM program name not found for " + name);
		} else {
			try {
				fem = FemProgram.valueOf(str);
			} catch (Exception e) {
				log.error("FEM program \"" + str + "\" not recognized for "
						+ name);
			}
		}
		String modelFile = props.getProperty(name + ".model.filename");
		FemConfig result = new FemConfig(address, dim, fem, modelFile, nodes);
		for (Integer node : nodes) {
			str = props.getProperty(name + ".effective.dofs." + node);
			List<DispDof> edofs = null;
			if (str == null) {
				log.error("Missing Effective DOFs for node " + node
						+ " substructure " + address);
				continue;
			}
			try {
				edofs = eoDispDofList.parse(str);
				result.addEffectiveDofs(node, edofs);
			} catch (Exception e) {
				log.error("Effective DOF list \"" + str
						+ "\" not recognized for node " + node
						+ " substructure " + name, e);
			}
		}
		return result;
	}

	/**
	 * Save a configuration.
	 */
	public final void save() {
		File configFile = new File(configFilePath);
		props = new Properties();
		List<String> sorted = new ArrayList<String>(femConfig.getSubstructCfgs().keySet());
		Collections.sort(sorted);
		String str = "";
		boolean first = true;
		for (String name : sorted) {
			saveSubStructure(name);
			str += (first ? "" : ", ") + name;
			first = false;
		}
		props.setProperty("work.dir", femConfig.getWorkDir());
		props.setProperty("substructures", str);
		for(FemProgramConfig fpCfg : femConfig.getFemProgramParameters().values()) {
			saveFemProgram(fpCfg);
		}

		Writer configW;
		try {
			configW = new FileWriter(configFile);
			props.store(configW, "");
		} catch (IOException e) {
			log.error("Unable to write to \"" + configFilePath + "\" because ",
					e);
		}
	}

	/**
	 * Save a configuration for a substructure.
	 * 
	 * @param name
	 *            Name of substructure.
	 */
	private void saveSubStructure(final String name) {
		FemConfig config = femConfig.getSubstructCfgs().get(name);
		props.setProperty(name + ".dimension", config.getDimension().name());
		props.setProperty(name + ".control.nodes",
				eoIntegerList.encode(config.getNodeSequence()));
		for (Integer node : config.getNodeSequence()) {
			if (config.getEffectiveDofs(node) == null) {
				log.error("Node " + node + " from " + name
						+ " has no effective DOFs");
				continue;
			}
			props.setProperty(name + ".effective.dofs." + node,
					eoDispDofList.encode(config.getEffectiveDofs(node)));
			node++;
		}
		props.setProperty(name + ".fem.program", config.getFemProgram().name());
		props.setProperty(name + ".model.filename", config.getModelFileName());
	}

	/**
	 * @param configFilePath
	 *            the configFilePath to set
	 */
	public final void setConfigFilePath(final String configFilePath) {
		this.configFilePath = configFilePath;
	}
}