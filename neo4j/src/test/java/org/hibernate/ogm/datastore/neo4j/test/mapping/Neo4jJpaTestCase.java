/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.datastore.neo4j.test.mapping;

import static org.fest.assertions.Assertions.assertThat;
import static org.hibernate.ogm.datastore.neo4j.test.dsl.GraphAssertions.assertThatExists;
import static org.hibernate.ogm.datastore.neo4j.test.dsl.GraphAssertions.assertThatExists;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.ogm.datastore.neo4j.impl.Neo4jDatastoreProvider;
import org.hibernate.ogm.datastore.neo4j.test.dsl.NodeForGraphAssertions;
import org.hibernate.ogm.datastore.neo4j.test.dsl.RelationshipsChainForGraphAssertions;
import org.hibernate.ogm.datastore.spi.DatastoreProvider;
import org.hibernate.ogm.jpa.impl.OgmEntityManagerFactory;
import org.hibernate.ogm.utils.jpa.JpaTestCase;
import org.junit.After;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.ResourceIterator;

/**
 * Common methods to check the mapping of entities in Neo4j.
 *
 * @author Davide D'Alto
 */
public abstract class Neo4jJpaTestCase extends JpaTestCase {

	@After
	public void deleteAll() throws Exception {
		executeQuery( "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n, r" );
	}

	protected void assertRelationships(int rel) throws Exception {
		assertThat( numberOfRelationships() ).as( "Unexpected number of relationships" ).isEqualTo( rel );
	}

	protected void assertNumberOfNodes(int nodes) throws Exception {
		assertThat( numberOfNodes() ).as( "Unexpected number of nodes" ).isEqualTo( nodes );
	}

	protected Long numberOfNodes() throws Exception {
		return executeQuery( "MATCH (n) RETURN COUNT(*)" );
	}

	protected Long numberOfRelationships() throws Exception {
		return executeQuery( "MATCH (n) - [r] -> () RETURN COUNT(r)" );
	}

	protected void assertExpectedMapping(String alias, String cypher, Map<String, Object> params) throws Exception {
		getTransactionManager().begin();
		ResourceIterator<Object> columnAs = executeCypherQuery( "MATCH " + cypher + " RETURN " + alias, params ).columnAs( alias );

		assertThat( columnAs.hasNext() ).as( cypher + " not found, cannot count properties" ).isTrue();
		PropertyContainer propertyContainer = (PropertyContainer) columnAs.next();
		Iterable<String> propertyKeys = propertyContainer.getPropertyKeys();
		List<String> unexpectedProperties = new ArrayList<String>();
		Set<String> expectedProperties = null;
		@SuppressWarnings("unchecked")
		Map<String, Object> expectedPropertiesMap = (Map<String, Object>) params.get( alias );
		if (expectedPropertiesMap != null) {
			expectedProperties = expectedPropertiesMap.keySet();
		}
		for ( Iterator<String> iterator = propertyKeys.iterator(); iterator.hasNext(); ) {
			String actual = iterator.next();
			if ( !expectedProperties.contains( actual ) ) {
				unexpectedProperties.add( actual );
			}
		}
		List<String> missingProperties = new ArrayList<String>();
		if ( expectedProperties != null ) {
			for ( String expected : expectedProperties ) {
				if ( !propertyContainer.hasProperty( expected ) ) {
					missingProperties.add( expected );
				}
			}
		}
		assertThat( unexpectedProperties ).as( "Unexpected properties for " + cypher ).isEmpty();
		assertThat( missingProperties ).as( "Missing properties for " + cypher ).isEmpty();
		assertThat( columnAs.hasNext() ).as( "Unexpected result returned" ).isFalse();
		getTransactionManager().commit();
	}

	protected ExecutionResult executeCypherQuery(String query, Map<String, Object> parameters) throws Exception {
		ExecutionEngine engine = createExecutionEngine();
		ExecutionResult result = engine.execute( query, parameters );
		return result;
	}

	protected ExecutionEngine createExecutionEngine() {
		SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) ( (OgmEntityManagerFactory) getFactory() ).getSessionFactory();
		Neo4jDatastoreProvider provider = (Neo4jDatastoreProvider) sessionFactory.getServiceRegistry().getService( DatastoreProvider.class );
		ExecutionEngine engine = new ExecutionEngine( provider.getDataBase() );
		return engine;
	}

	private Long executeQuery(String queryString) throws Exception {
		final EntityManager em = getFactory().createEntityManager();
		em.getTransaction().begin();
		@SuppressWarnings("unchecked")
		List<Object> results = em.createNativeQuery( queryString ).getResultList();
		Long uniqueResult = null;
		if ( !results.isEmpty() ) {
			uniqueResult = (Long) results.get( 0 );
		}
		em.getTransaction().commit();
		em.close();
		if ( uniqueResult == null ) {
			return null;
		}
		return uniqueResult;
	}

	protected void assertThatNodesExistOnly(ExecutionEngine executionEngine, NodeForGraphAssertions... nodes) throws Exception {
		for ( NodeForGraphAssertions node : nodes ) {
			assertThatExists( executionEngine, node );
		}
		assertNumberOfNodes( nodes.length );
	}

	protected void assertThatRelationshipsExistOnly(ExecutionEngine executionEngine, RelationshipsChainForGraphAssertions... relationships) throws Exception {
		int expectedNumberOfRelationships = 0;
		for ( RelationshipsChainForGraphAssertions relationship : relationships ) {
			assertThatExists( executionEngine, relationship );
			expectedNumberOfRelationships += relationship.getSize();
		}
		assertRelationships( expectedNumberOfRelationships );
	}
}
