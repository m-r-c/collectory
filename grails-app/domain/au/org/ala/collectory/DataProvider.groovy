package au.org.ala.collectory

class DataProvider extends ProviderGroup implements Serializable {

    static final String ENTITY_TYPE = 'DataProvider'
    static final String ENTITY_PREFIX = 'dp'

    static hasMany = [resources: DataResource]

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
        dps.resources = resources.collect {[it.uid, it.name]}
        return dps
    }

    long dbId() {
        return id;
    }

    String entityType() {
        return ENTITY_TYPE;
    }


}