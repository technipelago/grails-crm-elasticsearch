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

import org.elasticsearch.client.Client
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.transport.TransportAddress

/**
 * Created by goran on 2016-07-05.
 */
class ElasticsearchClientFactoryBean {

    def grailsApplication

    Client getInstance() {
        Client client = null
        if (grailsApplication.config.elasticsearch.enabled) {
            def config = grailsApplication.config.elasticsearch
            String clusterName = config.cluster.name ?: 'elasticsearch'
            //String indexName = config.index.name ?: 'grails'
            String username = config.username ?: 'elasticsearch'
            String password = config.password ?: 'password'
            Settings.Builder builder = Settings.builder().put("cluster.name", clusterName)
            //if (username && password) {
            //    builder.put("shield.user", "$username:$password".toString())
            //}
            List<Map> hosts = config.hosts ?: [host: 'localhost', port: 9300]
            List<TransportAddress> transportAddresses = hosts.collect {
                new InetSocketTransportAddress(InetAddress.getByName(it.host), it.port)
            }

            client = new PreBuiltTransportClient(builder.build())
            for(addr in transportAddresses) {
                client.addTransportAddress(addr)
            }
        }
        client
    }
}
