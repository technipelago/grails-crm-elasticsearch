/*
 * Copyright (c) 2016 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.elasticsearch

import grails.plugins.crm.core.TenantUtils
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders

/**
 * Created by goran on 2016-07-05.
 */
class ElasticsearchService {

    static transactional = false

    def grailsApplication
    def crmCoreService
    Client elasticsearchClient

    private String getSearchIndex() {
        grailsApplication.config.elasticsearch.index.name ?: 'grails'
    }

    SearchResponse search(Long tenant, String q, Map params) {
        int offset = Integer.parseInt((params.offset ?: 0).toString())
        int max = Integer.parseInt((params.max ?: 50).toString())
        QueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("tenant", tenant))
                .must(QueryBuilders.fuzzyQuery("_all", q).prefixLength(2))
        elasticsearchClient.prepareSearch(getSearchIndex())
                //.setTypes(*typeMappings.keySet().toArray())
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(query)
                .setFrom(offset).setSize(max).setExplain(true)
                .execute()
                .actionGet()
    }

    ActionResponse index(instance) {
        def ref = crmCoreService.getReferenceIdentifier(instance)
        def (type, id) = ref.split('@').toList()
        def json = event(namespace: type, topic: 'index', fork: false,
                data: [tenant: TenantUtils.tenant, id: instance.ident(), entity: instance])?.value
        if(json != null) {
            elasticsearchClient.prepareIndex(getSearchIndex(), type, id).setSource(json).get()
        }
    }
}
