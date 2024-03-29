/*
 * This file is part of the JNR project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazingdb.calcite.application;

/**
 * Class which holds main function. Listens in on a TCP socket
 * for protocol buffer requests and then processes these requests.
 * @author felipe
 *
 */
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.blazingdb.calcite.application.Chrono.Chronometer;
import com.blazingdb.protocol.IService;
import com.blazingdb.protocol.message.RequestMessage;
import com.blazingdb.protocol.message.ResponseErrorMessage;
import com.blazingdb.protocol.message.ResponseMessage;
import com.blazingdb.protocol.message.calcite.DDLCreateTableRequestMessage;
import com.blazingdb.protocol.message.calcite.DDLDropTableRequestMessage;
import com.blazingdb.protocol.message.calcite.DDLResponseMessage;
import com.blazingdb.protocol.message.calcite.DMLRequestMessage;
import com.blazingdb.protocol.message.calcite.DMLResponseMessage;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.dbcp.BasicDataSource;

import java.sql.Connection;
import java.sql.SQLException;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import javax.naming.NamingException;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import jnr.unixsocket.UnixServerSocket;
import jnr.unixsocket.UnixServerSocketChannel;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

import blazingdb.protocol.Status;
import blazingdb.protocol.calcite.MessageType;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import java.nio.charset.Charset;

public class CalciteApplication {

	private static void executeUpdate(final String dataDirectory) throws NamingException, SQLException,
			LiquibaseException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		// setDataSource((String) servletValueContainer.getValue(LIQUIBASE_DATASOURCE));

		final String LIQUIBASE_CHANGELOG = "liquibase.changelog";
		final String LIQUIBASE_DATASOURCE = "liquibase.datasource";

		String dataSourceName;
		String changeLogFile;
		String contexts;
		String labels;

		// setChangeLogFile((String) servletValueContainer.getValue(LIQUIBASE_CHANGELOG));
		changeLogFile = "liquibase-bz-master.xml";

		// setContexts((String) servletValueContainer.getValue(LIQUIBASE_CONTEXTS));
		contexts = "";
		// setLabels((String) servletValueContainer.getValue(LIQUIBASE_LABELS));

		labels = "";

		// defaultSchema = StringUtil.trimToNull((String)
		// servletValueContainer.getValue(LIQUIBASE_SCHEMA_DEFAULT));
		// defaultSchema =

		Connection connection = null;
		Database database = null;
		try {
			// DriverManager.registerDriver((Driver) Class.forName("com.mysql.jdbc.Driver").newInstance());
			// String url = "jdbc:mysql://localhost:3306/bz3";
			// connection = DriverManager.getConnection(url);

			BasicDataSource dataSource = new BasicDataSource();
			dataSource.setDriverClassName("org.h2.Driver");
			dataSource.setUsername("blazing");
			dataSource.setPassword("blazing");
			dataSource.setUrl("jdbc:h2:" + dataDirectory + "/bz3");
			dataSource.setMaxActive(10);
			dataSource.setMaxIdle(5);
			dataSource.setInitialSize(5);
			dataSource.setValidationQuery("SELECT 1");

			// MySQLData dataSource = new JdbcDataSource(); // (DataSource) ic.lookup(dataSourceName);
			// dataSource.setURL("jdbc:mysql://localhost:3306/bz3");
			// dataSource.setUser("blazing");
			// dataSource.setPassword("blazing");
			connection = dataSource.getConnection();

			Thread currentThread = Thread.currentThread();
			ClassLoader contextClassLoader = currentThread.getContextClassLoader();
			ResourceAccessor threadClFO = new ClassLoaderResourceAccessor(contextClassLoader);

			ResourceAccessor clFO = new ClassLoaderResourceAccessor();
			ResourceAccessor fsFO = new FileSystemResourceAccessor();

			database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));

			Liquibase liquibase = new Liquibase(changeLogFile, new CompositeResourceAccessor(clFO, fsFO, threadClFO),
					database);

			// @SuppressWarnings("unchecked")
			// StringTokenizer initParameters = new StringTokenizer(""); // servletContext.getInitParameterNames();
			// while (initParameters.hasMoreElements()) {
			// String name = initParameters.nextElement().trim();
			// if (name.startsWith(LIQUIBASE_PARAMETER + ".")) {
			// // liquibase.setChangeLogParameter(name.substring(LIQUIBASE_PARAMETER.length() + 1),
			// // servletValueContainer.getValue(name));
			// }
			// }

			liquibase.update(new Contexts(contexts), new LabelExpression(labels));
		} finally {
			if (database != null) {
				database.close();
			} else if (connection != null) {
				connection.close();
			}
		}
	}

	public static int bytesToInt(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(LITTLE_ENDIAN);
		return buffer.getInt();
	}

	public static byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	public static void main(String[] args) throws IOException {
		final CalciteApplicationOptions calciteApplicationOptions = parseArguments(args);

		final String dataDirectory = calciteApplicationOptions.dataDirectory();

		try {
			executeUpdate(dataDirectory);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Runnable service = null;
		
		final boolean isTCP = (calciteApplicationOptions.connectionType() == CalciteApplicationOptions.ConnectionType.TCP);
		
		if (isTCP == true) {
			final Integer tcpPort = calciteApplicationOptions.tcpPort();
			service = new TCPService(tcpPort, dataDirectory, calciteApplicationOptions.getOrchestratorIp(), calciteApplicationOptions.getOrchestratorPort());
		} else {
			final String unixSocketPath = calciteApplicationOptions.unixSocketPath();
			service = new UnixService(unixSocketPath, dataDirectory);
		}
		
		new Thread(service).start();
	}

	private static CalciteApplicationOptions parseArguments(String[] arguments) {
		final Options options = new Options();

		final String dataDirectoryDefaultValue = "/blazingsql";
		final Option dataDirectoryOption = Option.builder("d").required(false).longOpt("data_directory").hasArg()
				.argName("PATH").desc("Path to data directory where calcite put" + " the metastore files").build();
		options.addOption(dataDirectoryOption);

		final String tcpPortDefaultValue = "8891";
		final Option tcpPortOption = Option.builder("p").required(false).longOpt("port").hasArg().argName("INTEGER")
				.desc("TCP port for this service").type(Integer.class).build();
		options.addOption(tcpPortOption);

		final String unixSocketPathDefaultValue = "/tmp/calcite.socket";
		final Option unixSocketPathOption = Option.builder("u").required(false).longOpt("unix_socket_path").hasArg().argName("FILE PATH STRING")
				.desc("Unix socket file path for this service").build();
		options.addOption(unixSocketPathOption);

		final String orchestratorIpDefaultValue = "127.0.0.1";
		final Option orchestratorIp = Option.builder("oip").required(false).longOpt("orch_ip").hasArg()
				.argName("HOSTNAME").desc("Orchestrator hostname").build();
		options.addOption(orchestratorIp);

		final String orchestratorPortDefaultValue = "8889";
		final Option orchestratorPort = Option.builder("oport").required(false).longOpt("orch_port").hasArg()
				.argName("INTEGER").desc("Orchestrator port").build();
		options.addOption(orchestratorPort);

		try {
			final CommandLineParser commandLineParser = new DefaultParser();
			final CommandLine commandLine = commandLineParser.parse(options, arguments);

			final String dataDirectory = commandLine.getOptionValue(dataDirectoryOption.getLongOpt(),
					dataDirectoryDefaultValue);

			final Integer tcpPort = Integer.valueOf(commandLine.getOptionValue(tcpPortOption.getLongOpt(), tcpPortDefaultValue));
			
			final String unixSocketPath = commandLine.getOptionValue(unixSocketPathOption.getLongOpt(),
					unixSocketPathDefaultValue);

			final boolean hasTCPOpt = commandLine.hasOption("p");
			final boolean hasUnixSocketOpt = commandLine.hasOption("u");
			
			final boolean hasTCPOnly = (hasTCPOpt == true && hasUnixSocketOpt == false);
			final boolean hasEmptyProtocolArgs = (hasTCPOpt == false && hasUnixSocketOpt == false);
			final boolean isTCP = (hasTCPOnly == true && hasEmptyProtocolArgs == false);
			
			CalciteApplicationOptions calciteApplicationOptions = null;
			
			if (isTCP) {
				final String orchIp = commandLine.getOptionValue(orchestratorIp.getLongOpt(), orchestratorIpDefaultValue);
				final Integer orchPort = Integer.valueOf(commandLine.getOptionValue(orchestratorPort.getLongOpt(), orchestratorPortDefaultValue));

				calciteApplicationOptions = new CalciteApplicationOptions(tcpPort, dataDirectory, orchIp, orchPort);
			} else {
				calciteApplicationOptions = new CalciteApplicationOptions(unixSocketPath, dataDirectory);
			}
			
			return calciteApplicationOptions;
		} catch (ParseException e) {
			System.out.println(e);
			final HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("CalciteApplication", options);
			System.exit(1);
			return null;
		}
	}
	
	private static class CalciteApplicationOptions {
		public static enum ConnectionType {
			UNIX_SOCKET,
			TCP
		}
		
		private String unixSocketPath = null;
		private Integer tcpPort = null;
		private final String dataDirectory;
		private String  orchestratorIp = null;
		private Integer orchestratorPort = null;

		//ctor for TCP
		public CalciteApplicationOptions(final Integer tcpPort, final String dataDirectory, final String orchestratorIp, final Integer orchestratorPort) {
			this.tcpPort = tcpPort;
			this.dataDirectory = dataDirectory;
			this.orchestratorIp = orchestratorIp;
			this.orchestratorPort = orchestratorPort;
		}
		
		//ctor for Unix Socket
		public CalciteApplicationOptions(final String unixSocketPath, final String dataDirectory) {
			this.unixSocketPath = unixSocketPath;
			this.dataDirectory = dataDirectory;
		}
		
		public ConnectionType connectionType() {
			if (this.unixSocketPath == null && this.tcpPort != null) {
				return ConnectionType.TCP;
			}
			
			return ConnectionType.UNIX_SOCKET;
		}
		
		public String unixSocketPath() {
			return this.unixSocketPath;
		}

		public Integer tcpPort() {
			return this.tcpPort;
		}

		public String dataDirectory() {
			return dataDirectory;
		}

		public String getOrchestratorIp() {
			return orchestratorIp;
		}

		public Integer getOrchestratorPort() {
			return orchestratorPort;
		}
	}
}
