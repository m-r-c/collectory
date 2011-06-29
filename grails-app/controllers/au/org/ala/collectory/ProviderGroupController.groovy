package au.org.ala.collectory

import grails.converters.JSON
import org.springframework.web.multipart.MultipartFile
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent

/**
 * This is a base class for all provider group entities types.
 *
 * It provides common code for shared attributes like contacts.
 */
abstract class ProviderGroupController {

    static String entityName = "ProviderGroup"
    static String entityNameLower = "providerGroup"

    def idGeneratorService, authService

/*
 * Access control
 *
 * All methods require EDITOR role.
 * Edit methods require ADMIN or the user to be an administrator for the entity.
 */
    def beforeInterceptor = [action:this.&auth]
    def auth() {
        if (!authService.userInRole(ProviderGroup.ROLE_EDITOR)) {
            render "You are not authorised to access this page."
            return false
        }
    }
    // helpers for subclasses
    protected username = {
            authService?.username() ?: 'unavailable'
    }
    protected isAdmin = {
            authService?.isAdmin() ?: false
    }
/*
 End access control
 */

    /**
     * List providers for institutions/collections
     */
    def showProviders = {
        def provs = DataLink.findAllByConsumer(params.id).collect {it.provider}
        render provs as JSON
    }

    /**
     * List consumers of data resources/providers
     */
    def showConsumers = {
        def cons = DataLink.findAllByProvider(params.id).collect {it.consumer}
        render cons as JSON
    }

    /**
     * Checks for optimistic lock failure
     *
     * @param pg the entity being updated
     * @param view the view to return to if lock fails
     */
    def checkLocking = { pg, view ->
        if (params.version) {
            def version = params.version.toLong()
            if (pg.version > version) {
                println "db version = ${pg.version} submitted version = ${version}"
                pg.errors.rejectValue("version", "default.optimistic.locking.failure",
                        [message(code: "${pg.urlForm()}.label", default: pg.entityType())] as Object[],
                        "Another user has updated this ${pg.entityType()} while you were editing. This page has been refreshed with the current values.")
                println "error added - rendering ${view}"
                render(view: view, model: [command: pg])
            }
            return pg.version > version
        }
    }

    /**
     * Edit base attributes.
     * @param id
     */
    def edit = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (authService.isAuthorisedToEdit(pg.uid)) {
                params.page = params.page ?: '/shared/base'
                render(view:params.page, model:[command: pg, target: params.target])
            } else {
                render("You are not authorised to access this page.")
            }
        }
    }

    def editAttributions = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (authService.isAuthorisedToEdit(pg.uid)) {
                render(view: '/shared/attributions', model:[BCI: pg.hasAttribution('at1'), CHAH: pg.hasAttribution('at2'),
                        CHACM: pg.hasAttribution('at3'), command: pg])
            } else {
                render("You are not authorised to access this page.")
            }
        }
    }

    /**
     * Create a new entity instance.
     *
     */
    def create = {
        ProviderGroup pg
        switch (entityName) {
            case Collection.ENTITY_TYPE:
                pg = new Collection(uid: idGeneratorService.getNextCollectionId(), name: 'enter name', userLastModified: authService.username())
                if (params.institutionUid && Institution.findByUid(params.institutionUid)) {
                    pg.institution = Institution.findByUid(params.institutionUid)
                }
                break
            case Institution.ENTITY_TYPE:
                pg = new Institution(uid: idGeneratorService.getNextInstitutionId(), name: 'enter name', userLastModified: authService.username()); break
            case DataProvider.ENTITY_TYPE:
                pg = new DataProvider(uid: idGeneratorService.getNextDataProviderId(), name: 'enter name', userLastModified: authService.username()); break
            case DataResource.ENTITY_TYPE:
                pg = new DataResource(uid: idGeneratorService.getNextDataResourceId(), name: 'enter name', userLastModified: authService.username())
            if (params.dataProviderUid && DataProvider.findByUid(params.dataProviderUid)) {
                pg.dataProvider = DataProvider.findByUid(params.dataProviderUid)
            }
            break
            case DataHub.ENTITY_TYPE:
                pg = new DataHub(uid: idGeneratorService.getNextDataHubId(), name: 'enter name', userLastModified: authService.username()); break
        }
        if (!pg.hasErrors() && pg.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: "${pg.urlForm()}", default: pg.urlForm()), pg.uid])}"
            redirect(action: "show", id: pg.id)
        } else {
            flash.message = "Failed to create new ${entityName}"
            redirect(controller: 'admin', action: 'index')
        }
    }

    def cancel = {
        //println "Cancel - returnTo = ${params.returnTo}"
        if (params.returnTo) {
            redirect(uri: params.returnTo)
        } else {
            redirect(action: "show", id: params.id)
        }
    }

    /**
     * This does generic updating from a form. Works for all properties that can be bound by default.
     */
    def genericUpdate = { pg, view ->
        if (pg) {
            if (checkLocking(pg,view)) { return }

            pg.properties = params
            pg.userLastModified = username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                flash.message =
                  "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.id)
            }
            else {
                render(view: view, model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    /**
     * Update base attributes
     */
    def updateBase = {BaseCommand cmd ->
        if(cmd.hasErrors()) {
            cmd.errors.each {println it}
            cmd.id = params.id as int   // these do not seem to be injected
            cmd.version = params.version as int
            render(view:'/shared/base', model: [command: cmd])
        } else {
            def pg = get(params.id)
            if (pg) {
                if (checkLocking(pg,'/shared/base')) { return }

                // special handling for membership
                pg.networkMembership = toJson(params.networkMembership)
                params.remove('networkMembership')

                pg.properties = params
                pg.userLastModified = authService.username()
                if (!pg.hasErrors() && pg.save(flush: true)) {
                    flash.message =
                        "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                    redirect(action: "show", id: pg.id)
                }
                else {
                    render(view: "/shared/base", model: [command: pg])
                }
            } else {
                flash.message =
                    "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
                redirect(action: "show", id: params.id)
            }
        }
    }

    /**
     * Update descriptive attributes
     */
    def updateDescription = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'description')) { return }

            // do any entity specific processing
            entitySpecificDescriptionProcessing(pg, params)

            pg.properties = params
            pg.userLastModified = authService.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                flash.message =
                  "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.id)
            }
            else {
                render(view: "description", model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def entitySpecificDescriptionProcessing(pg, params) {
        // default is to do nothing
        // sub-classes override to do specific processing
    }
    
    /**
     * Update location attributes
     */
    def updateLocation = {LocationCommand cmd ->
        if(cmd.hasErrors()) {
            cmd.id = params.id as int   // these do not seem to be injected
            cmd.version = params.version as int
            render(view:'/shared/location', model: [command: cmd])
        } else {
            def pg = get(params.id)
            if (pg) {
                if (checkLocking(pg,'/shared/location')) { return }

                // special handling for lat & long
                if (!params.latitude) { params.latitude = -1 }
                if (!params.longitude) { params.longitude = -1 }

                // special handling for embedded address - need to create address obj if none exists and we have data
                if (!pg.address && [params.address?.street, params.address?.postBox, params.address?.city,
                    params.address?.state, params.address?.postcode, params.address?.country].join('').size() > 0) {
                    pg.address = new Address()
                }

                // special handling for embedded postal address - need to create address obj if none exists and we have data
                /*if (!pg.postalAddress && [params.postalAddress?.street, params.postalAddress?.postBox, params.postalAddress?.city,
                    params.postalAddress?.state, params.postalAddress?.postcode, params.postalAddress?.country].join('').size() > 0) {
                    pg.postalAddress = new Address()
                }*/

                pg.properties = params
                pg.userLastModified = authService.username()
                if (!pg.hasErrors() && pg.save(flush: true)) {
                    flash.message =
                      "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                    redirect(action: "show", id: pg.id)
                } else {
                    render(view: "/shared/location", model: [command: pg])
                }
            } else {
                flash.message =
                    "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
                redirect(action: "show", id: params.id)
            }
        }
    }

    def updateTaxonomyHints = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/editTaxonomyHints')) { return }

            // handle taxonomy hints
            def ranks = params.findAll { key, value ->
                key.startsWith('rank_') && value
            }
            def hints = ranks.sort().collect { key, value ->
                def idx = key.substring(5)
                def name = params."name_${idx}"
                return ["${value}": name]
            }
            def coverage = [coverage: hints]
            pg.taxonomyHints = coverage as JSON
        
            pg.userLastModified = authService.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                flash.message =
                  "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.id)
            }
            else {
                render(view: "description", model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def updateContactRole = {
        def contactFor = ContactFor.get(params.contactForId)
        if (contactFor) {
            contactFor.properties = params
            contactFor.userLastModified = authService.username()
            if (!contactFor.hasErrors() && contactFor.save(flush: true)) {
                flash.message = "${message(code: 'contactRole.updated.message')}"
                redirect(action: "edit", id: params.id, params: [page: '/shared/showContacts'])
            } else {
                render(view: '/shared/contactRole', model: [command: ProviderGroup._get(params.id), cf: contactFor])
            }

        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.label', default: "Contact for ${entityNameLower}"), params.contactForId])}"
            redirect(action: "show", id: params.id)
        }
    }

    def addContact = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            if (authService.isAuthorisedToEdit(pg.uid)) {
                Contact contact = Contact.get(params.addContact)
                if (contact) {
                    pg.addToContacts(contact, "editor", true, false, authService.username())
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: params.id)
                }
            } else {
                render("You are not authorised to access this page.")
            }
        }
    }

    def addNewContact = {
        def pg = get(params.id)
        def contact = Contact.get(params.contactId)
        if (contact && pg) {
            // add the contact to the collection
            pg.addToContacts(contact, "editor", true, false, authService.username())
            redirect(action: "edit", params: [page:"/shared/showContacts"], id: pg.id)
        } else {
            if (!pg) {
                flash.message = "Contact was created but ${entityNameLower} could not be found. Please edit ${entityNameLower} and add contact from existing."
                redirect(action: "list")
            } else {
                if (authService.isAuthorisedToEdit(pg.uid)) {
                    // contact must be null
                    flash.message = "Contact was created but could not be added to the ${pg.urlForm()}. Please add contact from existing."
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: pg.id)
                } else {
                    render("You are not authorised to access this page.")
                }
            }
        }
    }

    def removeContact = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (authService.isAuthorisedToEdit(pg.uid)) {
                ContactFor cf = ContactFor.get(params.idToRemove)
                if (cf) {
                    cf.delete()
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: params.id)
                }
            } else {
                render("You are not authorised to access this page.")
            }
        }
    }

    def editRole = {
        def contactFor = ContactFor.get(params.id)
        if (!contactFor) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.label', default: "Contact for ${entityNameLower}"), params.id])}"
            redirect(action: "list")
        } else {
            ProviderGroup pg = ProviderGroup._get(contactFor.entityUid)
            if (pg) {
                // are they allowed to edit
                if (authService.isAuthorisedToEdit(pg.uid)) {
                    render(view: '/shared/contactRole', model: [command: pg, cf: contactFor, returnTo: params.returnTo])
                } else {
                    render("You are not authorised to access this page.")
                }
            } else {
                flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.entityUid.label', default: "Entity for ${entityNameLower}"), params.id])}"
                redirect(action: "list")
            }
        }
    }

    /**
     * Get the instance for this entity based on either uid or DB id.
     * All sub-classes must implement this method.
     *
     * @param id UID or DB id
     * @return the entity of null if not found
     */
    abstract protected ProviderGroup get(id)

    /**
     * Update images
     */
    def updateImages = {
        def pg = get(params.id)
        def target = params.target ?: "imageRef"
        if (pg) {
            if (checkLocking(pg,'/shared/images')) { return }

            // special handling for uploading image
            // we need to account for:
            //  a) upload of new image
            //  b) change of metadata for existing image
            // removing an image is handled separately
            MultipartFile file
            switch (target) {
                case 'imageRef': file = params.imageFile; break
                case 'logoRef': file = params.logoFile; break
            }
            if (file?.size) {  // will only have size if a file was selected
                // save the chosen file
                if (file.size < 200000) {   // limit file to 200Kb
                    def filename = file.getOriginalFilename()
                    log.debug "filename=${filename}"

                    // update filename
                    pg."${target}" = new Image(file: filename)
                    String subDir = pg.urlForm()

                    def colDir = new File(ConfigurationHolder.config.repository.location.images as String, subDir)
                    colDir.mkdirs()
                    File f = new File(colDir, filename)
                    log.debug "saving ${filename} to ${f.absoluteFile}"
                    file.transferTo(f)
                    ActivityLog.log authService.username(), authService.isAdmin(), Action.UPLOAD_IMAGE, filename
                } else {
                    println "reject file of size ${file.size}"
                    pg.errors.rejectValue('imageRef', 'image.too.big', 'The image you selected is too large. Images are limited to 200KB.')
                    render(view: "/shared/images", model: [command: pg, target: target])
                    return
                }
            }
            pg.properties = params
            pg.userLastModified = authService.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.id)
            } else {
                render(view: "/shared/images", model: [command: pg, target: target])
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityName), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def removeImage = {
        def pg = get(params.id)
        if (pg) {
            if (authService.isAuthorisedToEdit(pg.uid)) {
                if (checkLocking(pg,'/shared/images')) { return }

                if (params.target == 'logoRef') {
                    pg.logoRef = null
                } else {
                    pg.imageRef = null
                }
                pg.userLastModified = authService.username()
                if (!pg.hasErrors() && pg.save(flush: true)) {
                    flash.message = "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                    redirect(action: "show", id: pg.id)
                } else {
                    render(view: "/shared/images", model: [command: pg])
                }
            } else {
                render("You are not authorised to access this page.")
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def updateAttributions = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/attributions')) { return }

            if (params.BCI && !pg.hasAttribution('at1')) {
                pg.addAttribution 'at1'
            }
            if (!params.BCI && pg.hasAttribution('at1')) {
                pg.removeAttribution 'at1'
            }
            if (params.CHAH && !pg.hasAttribution('at2')) {
                pg.addAttribution 'at2'
            }
            if (!params.CHAH && pg.hasAttribution('at2')) {
                pg.removeAttribution 'at2'
            }
            if (params.CHACM && !pg.hasAttribution('at3')) {
                pg.addAttribution 'at3'
            }
            if (!params.CHACM && pg.hasAttribution('at3')) {
                pg.removeAttribution 'at3'
            }

            if (pg.isDirty()) {
                pg.userLastModified = authService.username()
                if (!pg.hasErrors() && pg.save(flush: true)) {
                    flash.message =
                      "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                    redirect(action: "show", id: pg.id)
                }
                else {
                    render(view: "description", model: [command: pg])
                }
            } else {
                redirect(action: "show", id: pg.id)
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def delete = {
        def pg = get(params.id)
        if (pg) {
            if (authService.isAdmin()) {
                def name = pg.name
                log.info ">>${authService.username()} deleting ${entityName} " + name
                ActivityLog.log authService.username(), authService.isAdmin(), pg.uid, Action.DELETE
                try {
                    // remove contact links (does not remove the contact)
                    ContactFor.findAllByEntityUid(pg.uid).each {
                        log.info "Removing link to contact " + it.contact?.buildName()
                        it.delete()
                    }
                    // delete
                    pg.delete(flush: true)
                    flash.message = "${message(code: 'default.deleted.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), name])}"
                    redirect(action: "list")
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), name])}"
                    redirect(action: "show", id: params.id)
                }
            } else {
                render("You are not authorised to access this page.")
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        }
    }

    def showChanges = {
        def instance = get(params.id)
        if (instance) {
            // get audit records
            def changes = AuditLogEvent.findAllByUri(instance.uid,[sort:'lastUpdated',order:'desc'])
            render(view:'/shared/showChanges', model:[changes:changes, instance:instance])
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        }
    }

    def getChanges(uid) {
        // get audit records
        return AuditLogEvent.findAllByUri(uid,[sort:'lastUpdated',order:'desc',max:10])
    }

    protected String toJson(param) {
        if (!param) {
            return ""
        }
        if (param instanceof String) {
            // single value
            return ([param] as JSON).toString()
        }
        def list = param.collect {
            it.toString()
        }
        return (list as JSON).toString()
    }

    protected String toSpaceSeparatedList(param) {
        if (!param) {
            return ""
        }
        if (param instanceof String) {
            // single value
            return param
        }
        return param.join(' ')
    }

}
