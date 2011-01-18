package au.org.ala.collectory

class DataProvider extends ProviderGroup implements Serializable {

    static final String ENTITY_TYPE = 'DataProvider'
    static final String ENTITY_PREFIX = 'dp'

    static auditable = [ignore: ['version','dateCreated','lastUpdated','userLastModified']]

    static hasMany = [resources: DataResource]

    static mapping = {
        sort: 'name'
    }

    static constraints = {
    }

    boolean canBeMapped() {
        return false;
    }

    /**
     * Returns a summary of the data provider including:
     * - id
     * - name
     * - acronym
     * - lsid if available
     * - description
     * - provider codes for matching with biocache records
     *
     * @return CollectionSummary
     */
    DataProviderSummary buildSummary() {
        DataProviderSummary dps = init(new DataProviderSummary()) as DataProviderSummary
        // safety
        if (resources) {
            def list = []
            resources.each {
                if (it.hasProperty('uid')) {
                    list << [it.uid, it.name]
                } else {
                    log.error("problem accessing resources for uid = " + uid)
                }
            }
            dps.resources = list
        }
        def consumers = listConsumers()
        consumers.each {
            def pg = ProviderGroup._get(it)
            if (pg) {
                if (it[0..1] == 'co') {
                    dps.relatedCollections << [uid: pg.uid, name: pg.name]
                } else {
                    dps.relatedInstitutions << [uid: pg.uid, name: pg.name]
                }
            }
        }
        return dps
    }

    long dbId() {
        return id;
    }

    String entityType() {
        return ENTITY_TYPE;
    }


}
