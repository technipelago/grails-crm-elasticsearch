package grails.plugins.crm.elasticsearch

import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.elasticsearch.client.Client
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.*
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Created by goran on 2016-03-18.
 */
class AuditEventListener extends AbstractPersistenceEventListener {

    private static final Logger log = Logger.getLogger(this)

    private static final String SEARCHABLE_PROPERTY = 'searchable'

    /** List of pending objects to reindex. */
    static ThreadLocal<Map> pendingObjects = new ThreadLocal<Map>()

    /** List of pending object to delete */
    static ThreadLocal<Map> deletedObjects = new ThreadLocal<Map>()

    private final IndexRequestQueue indexRequestQueue


    AuditEventListener(ApplicationContext ctx, Datastore datastore, Client client, String indexName) {
        super(datastore)
        indexRequestQueue = new IndexRequestQueue(ctx, client, indexName)
    }

    private String createKey(entity) {
        GrailsNameUtils.getPropertyName(entity.class) + '@' + entity.id
    }

    @Override
    @CompileStatic
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (event instanceof PostInsertEvent) {
            onPostInsert(event)
        } else if (event instanceof PostUpdateEvent) {
            onPostUpdate(event)
        } else if (event instanceof PostDeleteEvent) {
            onPostDelete(event)
        }
    }

    @Override
    @CompileStatic
    boolean supportsEventType(Class<? extends ApplicationEvent> aClass) {
        [PostInsertEvent, PostUpdateEvent, PostDeleteEvent].any() { it.isAssignableFrom(aClass) }
    }

    void registerMySynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof IndexSynchronization) {
                    // already registered.
                    return
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new IndexSynchronization(indexRequestQueue, this))
        }
    }

    /**
     * Push object to index. Save as pending if transaction is not committed yet.
     */
    void pushToIndex(entity) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Save object as pending
            def objs = pendingObjects.get()
            if (!objs) {
                objs = [:]
                pendingObjects.set(objs)
            }

            def key = createKey(entity)
            if (deletedObjects.get()) {
                deletedObjects.get().remove(key)
            }
            objs[key] = entity
            registerMySynchronization()
            log.debug("Adding $key for later")
        } else {
            indexRequestQueue.addIndexRequest(entity)
            indexRequestQueue.executeRequests()
            log.debug("Adding $entity immediately")
        }
    }

    void pushToDelete(entity) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Add to list of deleted
            def objs = deletedObjects.get()
            if (!objs) {
                objs = [:]
                deletedObjects.set(objs)
            }

            def key = createKey(entity)
            if (pendingObjects.get()) {
                pendingObjects.get().remove(key)
            }
            objs[key] = entity
            registerMySynchronization()
            log.debug("Delete $key for later")
        } else {
            indexRequestQueue.addDeleteRequest(entity)
            indexRequestQueue.executeRequests()
            log.debug("Delete $entity immediately")
        }
    }

    @CompileStatic
    void onPostInsert(PostInsertEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            log.warn('Received a PostInsertEvent with no entity')
            return
        }
        if (GrailsClassUtils.isStaticProperty(entity.class, SEARCHABLE_PROPERTY)) {
            pushToIndex(entity)
        }
    }

    @CompileStatic
    void onPostUpdate(PostUpdateEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            log.warn('Received a PostUpdateEvent with no entity')
            return
        }
        if (GrailsClassUtils.isStaticProperty(entity.class, SEARCHABLE_PROPERTY)) {
            pushToIndex(entity)
        }
    }

    @CompileStatic
    void onPostDelete(PostDeleteEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            log.warn('Received a PostDeleteEvent with no entity')
            return
        }
        if (GrailsClassUtils.isStaticProperty(entity.class, SEARCHABLE_PROPERTY)) {
            pushToDelete(entity)
        }
    }

    @CompileStatic
    Map getPendingObjects() {
        pendingObjects.get()
    }

    @CompileStatic
    Map getDeletedObjects() {
        deletedObjects.get()
    }

    @CompileStatic
    void clearPendingObjects() {
        pendingObjects.remove()
    }

    @CompileStatic
    void clearDeletedObjects() {
        deletedObjects.remove()
    }

    @CompileStatic
    private def getEventEntity(AbstractPersistenceEvent event) {
        if (event.entityAccess) {
            return event.entityAccess.entity
        }

        event.entityObject
    }

}
