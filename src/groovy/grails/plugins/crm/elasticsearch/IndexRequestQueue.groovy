package grails.plugins.crm.elasticsearch

import grails.util.GrailsNameUtils
import groovy.json.JsonBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.elasticsearch.client.Client
import org.grails.plugin.platform.events.EventMessage
import org.springframework.context.ApplicationContext

/**
 * Holds objects to be indexed.
 * <br/>
 * NOTE: This is shared class, so need to be thread-safe.
 */
@CompileStatic
class IndexRequestQueue {

    def grailsEventsPublisher

    private final Client client
    private final String indexName

    /**
     * A map containing the pending index requests.
     */
    private Map<String, Object> indexRequests = [:]

    /**
     * A set containing the pending delete requests.
     */
    private Set<String> deleteRequests = []

    private Map<String, Map> mappings = [:]

    public IndexRequestQueue(ApplicationContext ctx, Client client, String indexName) {
        this.client = client
        this.indexName = indexName
        this.grailsEventsPublisher = ctx.getBean('grailsEventsPublisher')
    }

    @CompileDynamic
    private String createKey(instance) {
        GrailsNameUtils.getPropertyName(instance.class) + '@' + instance.id
    }

    void addIndexRequest(instance) {
        indexRequests.put(createKey(instance), instance)
    }

    void addDeleteRequest(instance) {
        synchronized (this) {
            deleteRequests.add(createKey(instance))
        }
    }

    @CompileDynamic
    void executeRequests() {
        Map<String, Object> toIndex = [:]
        Set<String> toDelete = []

        // Copy existing queue to ensure we are interfering with incoming requests.
        synchronized (this) {
            toIndex.putAll(indexRequests)
            toDelete.addAll(deleteRequests)
            indexRequests.clear()
            deleteRequests.clear()
        }

        // If there are domain instances that are both in the index requests & delete requests list,
        // they are directly deleted.
        toIndex.keySet().removeAll(toDelete)

        // If there is nothing in the queues, just stop here
        if (toIndex.isEmpty() && toDelete.empty) {
            return
        }

        toIndex.each { String key, Object value ->
            def (type, id) = key.split('@').toList()
            if (mappings[type] == null) {
                mappings[type] = initMapping(type)
            }
            def json = event(type, Long.valueOf(id), value)
            if (json != null) {
                println "Indexing $key $value ..."
                def response = client.prepareIndex(indexName, type, id).setSource(json).get()
                println "Index response=$response"
            } else {
                println "No mapping for $type"
            }
        }

        // Execute delete requests
        toDelete.each { String key ->
            def (type, id) = key.split('@').toList()
            println "Deleting $key ..."
            def response = client.prepareDelete(indexName, type, id).get()
            println "Delete response=$response"
        }
    }

    @CompileDynamic
    private Object event(String type, Long id, Object value) {
        grailsEventsPublisher.event(new EventMessage("index", [id: id, entity: value], type, true))?.value
    }

    @CompileDynamic
    private Boolean initMapping(String type) {
        def map = grailsEventsPublisher.event(new EventMessage("mapping", [:], type, false))?.value
        if (map != null) {
            def response = client.admin().indices().prepareExists(indexName).execute().actionGet()
            if (!response.exists) {
                response = client.admin().indices().prepareCreate(indexName).execute().actionGet()
                if (!response.isAcknowledged()) {
                    throw new RuntimeException("Failed to create index: $indexName")
                }
            }
            client.admin().indices()
                    .preparePutMapping(indexName)
                    .setType(type)
                    .setSource(new JsonBuilder(map).toPrettyString())
                    .execute().actionGet()
            return Boolean.TRUE
        }
        return Boolean.FALSE
    }
}
