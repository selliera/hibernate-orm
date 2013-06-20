/*
 * jDocBook, processing of DocBook sources
 *
 * Copyright (c) 2013, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.loader.plan.spi;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.LockMode;
import org.hibernate.engine.FetchStrategy;
import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.loader.plan.exec.process.internal.Helper;
import org.hibernate.loader.plan.spi.build.LoadPlanBuildingContext;
import org.hibernate.loader.plan.exec.process.spi.ResultSetProcessingContext;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.walking.spi.AssociationAttributeDefinition;
import org.hibernate.persister.walking.spi.AttributeDefinition;
import org.hibernate.type.CompositeType;

/**
 * Represents a {@link Fetch} for a composite attribute as well as a
 * {@link FetchOwner} for any sub-attributes fetches.
 *
 * @author Steve Ebersole
 * @author Gail Badner
 */
public class CompositeFetch extends AbstractSingularAttributeFetch {
	private static final FetchStrategy FETCH_PLAN = new FetchStrategy( FetchTiming.IMMEDIATE, FetchStyle.JOIN );

	private final CompositeBasedSqlSelectFragmentResolver sqlSelectFragmentResolver;

	/**
	 * Constructs a {@link CompositeFetch} object.
	 *
	 * @param sessionFactory - the session factory.
	 * @param owner - the fetch owner for this fetch.
	 * @param fetchedAttribute - the owner's property referring to this fetch.
	 */
	public CompositeFetch(
			SessionFactoryImplementor sessionFactory,
			final FetchOwner owner,
			final AttributeDefinition fetchedAttribute) {
		super( sessionFactory, owner, fetchedAttribute, FETCH_PLAN );

		this.sqlSelectFragmentResolver = new CompositeBasedSqlSelectFragmentResolver(
				sessionFactory,
				(CompositeType) fetchedAttribute.getType(),
				new CompositeBasedSqlSelectFragmentResolver.BaseSqlSelectFragmentResolver() {
					@Override
					public String[] toSqlSelectFragments(String alias) {
						return owner.toSqlSelectFragmentResolver().toSqlSelectFragments( alias, fetchedAttribute );
					}
				}
		);
	}

	public CompositeFetch(CompositeFetch original, CopyContext copyContext, FetchOwner fetchOwnerCopy) {
		super( original, copyContext, fetchOwnerCopy );
		this.sqlSelectFragmentResolver = original.sqlSelectFragmentResolver;
	}

	@Override
	public SqlSelectFragmentResolver toSqlSelectFragmentResolver() {
		return sqlSelectFragmentResolver;
	}

	@Override
	public EntityPersister retrieveFetchSourcePersister() {
		return getOwner().retrieveFetchSourcePersister();
	}

	@Override
	public void hydrate(ResultSet resultSet, ResultSetProcessingContext context) throws SQLException {
		// anything to do?
	}

	@Override
	public Object resolve(ResultSet resultSet, ResultSetProcessingContext context) throws SQLException {
		// anything to do?
		return null;
	}

	@Override
	public void read(ResultSet resultSet, ResultSetProcessingContext context, Object owner) throws SQLException {
		final EntityReference ownerEntityReference = Helper.INSTANCE.findOwnerEntityReference( this );
		final ResultSetProcessingContext.EntityReferenceProcessingState entityReferenceProcessingState = context.getProcessingState(
				ownerEntityReference
		);
		final EntityKey entityKey = entityReferenceProcessingState.getEntityKey();
		final Object entity = context.resolveEntityKey( entityKey, Helper.INSTANCE.findOwnerEntityReference( (FetchOwner) ownerEntityReference ) );
		for ( Fetch fetch : getFetches() ) {
			fetch.read( resultSet, context, entity );
		}
	}

	@Override
	public CompositeFetch makeCopy(CopyContext copyContext, FetchOwner fetchOwnerCopy) {
		copyContext.getReturnGraphVisitationStrategy().startingCompositeFetch( this );
		final CompositeFetch copy = new CompositeFetch( this, copyContext, fetchOwnerCopy );
		copyContext.getReturnGraphVisitationStrategy().finishingCompositeFetch( this );
		return copy;
	}

	@Override
	public CollectionFetch buildCollectionFetch(
			AssociationAttributeDefinition attributeDefinition,
			FetchStrategy fetchStrategy,
			LoadPlanBuildingContext loadPlanBuildingContext) {
		return new CollectionFetch(
				loadPlanBuildingContext.getSessionFactory(),
				LockMode.NONE, // todo : for now
				this,
				fetchStrategy,
				attributeDefinition
		);
	}
}
