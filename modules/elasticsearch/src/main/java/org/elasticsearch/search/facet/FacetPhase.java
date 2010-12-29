/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.facet;

import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.search.NoopCollector;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.search.SearchParseElement;
import org.elasticsearch.search.SearchPhase;
import org.elasticsearch.search.internal.ContextIndexSearcher;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.query.QueryPhaseExecutionException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class FacetPhase implements SearchPhase {

    private final FacetParseElement facetParseElement;

    @Inject public FacetPhase(FacetParseElement facetParseElement) {
        this.facetParseElement = facetParseElement;
    }

    @Override public Map<String, ? extends SearchParseElement> parseElements() {
        return ImmutableMap.of("facets", facetParseElement);
    }

    @Override public void preProcess(SearchContext context) {
    }

    @Override public void execute(SearchContext context) throws ElasticSearchException {
        if (context.facets() == null) {
            return;
        }
        if (context.queryResult().facets() != null) {
            // no need to compute the facets twice, they should be computed on a per context basis
            return;
        }

        // run global facets ...
        if (context.searcher().hasCollectors(ContextIndexSearcher.Scopes.GLOBAL)) {
            Query query = Queries.MATCH_ALL_QUERY;
            if (context.types().length > 0) {
                query = new FilteredQuery(query, context.filterCache().cache(context.mapperService().typesFilter(context.types())));
            }

            context.searcher().processingScope(ContextIndexSearcher.Scopes.GLOBAL);
            try {
                context.searcher().search(query, NoopCollector.NOOP_COLLECTOR);
            } catch (IOException e) {
                throw new QueryPhaseExecutionException(context, "Failed to execute global facets", e);
            } finally {
                context.searcher().processedScope();
            }
        }
        SearchContextFacets contextFacets = context.facets();

        List<Facet> facets = Lists.newArrayListWithCapacity(2);
        if (contextFacets.facetCollectors() != null) {
            for (FacetCollector facetCollector : contextFacets.facetCollectors()) {
                facets.add(facetCollector.facet());
            }
        }
        context.queryResult().facets(new InternalFacets(facets));
    }
}