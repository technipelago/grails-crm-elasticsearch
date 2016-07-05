import grails.plugins.crm.elasticsearch.ElasticsearchClientFactoryBean
import org.elasticsearch.client.Client

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

class CrmElasticsearchGrailsPlugin {
    def groupId = ""
    def version = "1.0.0-SNAPSHOT"
    def grailsVersion = "2.2 > *"
    def dependsOn = [:]
    def loadAfter = ['crmCore']
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "src/groovy/grails/plugins/crm/elasticsearch/TestSecurityDelegate.groovy"
    ]
    def title = "Elasticsearch indexing for GR8 CRM"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''
Provides Elasticsearch indexing features for GR8 CRM applications.
'''
    def documentation = "http://gr8crm.github.io/plugins/crm-elasticsearch/"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/goeh/grails-crm-elasticsearch/issues"]
    def scm = [url: "https://github.com/goeh/grails-crm-elasticsearch"]

    def doWithSpring = {
        if (application.config.elasticsearch.enabled) {
            elasticsearchClientFactory(ElasticsearchClientFactoryBean) {
                grailsApplication = application
            }
            elasticsearchClient(elasticsearchClientFactory: 'getInstance')
        }
    }

    def doWithApplicationContext = { ctx ->
        if (application.config.elasticsearch.enabled) {
            Client client = ctx.getBean('elasticsearchClient')
            String indexName = application.config.elasticsearch.index.name ?: 'grails'
            ctx.eventTriggeringInterceptor.datastores.each { key, datastore ->
                applicationContext.addApplicationListener(new grails.plugins.crm.elasticsearch.AuditEventListener(ctx, datastore, client, indexName))
            }
        }
    }
}
