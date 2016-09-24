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
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.SearchHits

/**
 * Created by goran on 2016-03-30.
 */
class ElasticsearchController {

    static allowedMethods = [search: 'POST']

    def elasticsearchService
    def crmCoreService

    def index(String q) {
        def model = [:]

        params.max = Math.min(params.max ? params.int('max') : 10, 100)

        if(q) {
            SearchResponse searchResponse = elasticsearchService.search(TenantUtils.tenant, q, params)
            SearchHits hits = searchResponse.hits
            model.totalCount = hits.totalHits
            model.searchResult = hits.hits
            model.result = hits.hits.collect{
                crmCoreService.getReference(it.type + '@' + it.id) ?: [error: it]
            }
        }
        model
    }
}
