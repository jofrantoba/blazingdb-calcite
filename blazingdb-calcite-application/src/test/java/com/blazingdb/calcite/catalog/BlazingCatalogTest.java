package com.blazingdb.calcite.catalog;

import static org.testng.Assert.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.commons.dbcp.BasicDataSource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.hibernate.Criteria;
import org.hibernate.LazyInitializationException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import com.blazingdb.calcite.catalog.domain.CatalogColumnDataTypeImpl;
import com.blazingdb.calcite.plan.BlazingPlanner;

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

public class BlazingCatalogTest {

	private BlazingPlanner blazingCalcite;
	private static SessionFactory sessionFactory = null;

	private static final String LIQUIBASE_CHANGELOG = "liquibase.changelog";
	private static final String LIQUIBASE_DATASOURCE = "liquibase.datasource";
	private static final String LIQUIBASE_PARAMETER = "liquibase.parameter";
	private static final String LIQUIBASE_CONTEXTS = "liquibase.contexts";
	private static final String LIQUIBASE_LABELS = "liquibase.labels";

	private String dataSourceName;
	private String changeLogFile;
	private String contexts;
	private String labels;

	private void executeUpdate() throws NamingException, SQLException, LiquibaseException, InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		// setDataSource((String) servletValueContainer.getValue(LIQUIBASE_DATASOURCE));

		this.dataSourceName = "bz3";

		if (this.dataSourceName == null) {
			throw new RuntimeException("Cannot run Liquibase, " + LIQUIBASE_DATASOURCE + " is not set");
		}

		// setChangeLogFile((String) servletValueContainer.getValue(LIQUIBASE_CHANGELOG));

		String changeLogFile = "liquibase-bz-master.xml";
		this.changeLogFile = changeLogFile;

		if (this.changeLogFile == null) {
			throw new RuntimeException("Cannot run Liquibase, " + LIQUIBASE_CHANGELOG + " is not set");
		}

		// setContexts((String) servletValueContainer.getValue(LIQUIBASE_CONTEXTS));
		this.contexts = "";
		// setLabels((String) servletValueContainer.getValue(LIQUIBASE_LABELS));

		this.labels = "";

		// this.defaultSchema = StringUtil.trimToNull((String)
		// servletValueContainer.getValue(LIQUIBASE_SCHEMA_DEFAULT));
		// this.defaultSchema =

		Connection connection = null;
		Database database = null;
		try {
			// DriverManager.registerDriver((Driver) Class.forName("com.mysql.jdbc.Driver").newInstance());
			// String url = "jdbc:mysql://localhost:3306/bz3";
			// connection = DriverManager.getConnection(url);

			BasicDataSource dataSource = new BasicDataSource();
			dataSource.setDriverClassName("com.mysql.jdbc.Driver");
			dataSource.setUsername("blazing");
			dataSource.setPassword("blazing");
			dataSource.setUrl("jdbc:mysql://localhost:3306/bz3");
			dataSource.setMaxActive(10);
			dataSource.setMaxIdle(5);
			dataSource.setInitialSize(5);
			dataSource.setValidationQuery("SELECT 1");

			// MySQLData dataSource = new JdbcDataSource(); // (DataSource) ic.lookup(this.dataSourceName);
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
			database.setDefaultSchemaName(this.dataSourceName);
			Liquibase liquibase = new Liquibase(this.changeLogFile,
					new CompositeResourceAccessor(clFO, fsFO, threadClFO), database);

			// @SuppressWarnings("unchecked")
			// StringTokenizer initParameters = new StringTokenizer(""); // servletContext.getInitParameterNames();
			// while (initParameters.hasMoreElements()) {
			// String name = initParameters.nextElement().trim();
			// if (name.startsWith(LIQUIBASE_PARAMETER + ".")) {
			// // liquibase.setChangeLogParameter(name.substring(LIQUIBASE_PARAMETER.length() + 1),
			// // servletValueContainer.getValue(name));
			// }
			// }

			liquibase.update(new Contexts(this.contexts), new LabelExpression(this.labels));
		} finally {
			if (database != null) {
				database.close();
			} else if (connection != null) {
				connection.close();
			}
		}
	}

	@BeforeMethod
	public void setUp() throws Exception {
		this.executeUpdate();
		sessionFactory = new Configuration().configure().buildSessionFactory();

	}

	@AfterMethod
	public void tearDown() throws Exception {
		sessionFactory.close();
	}

	@Test()
	public void testLoadDataInFile()
			throws RelConversionException, ValidationException, SqlParseException, IOException, SQLException {

		System.out.println("Hello, World");

		System.out.println("testSaveOperation begins ........ This is \"C\" of CRUD");

		CatalogColumnDataTypeImpl type1 = new CatalogColumnDataTypeImpl("double");

		Session session = sessionFactory.openSession();
		session.beginTransaction();

		session.save(type1);

		session.getTransaction().commit();
		session.close();
		System.out.println("testSaveOperation ends .......");

		System.out.println("BYE, World");

	}

}