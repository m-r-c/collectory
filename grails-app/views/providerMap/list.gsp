
<%@ page import="au.org.ala.collectory.ProviderMap" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'providerMap.label', default: 'ProviderMap')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
        <div class="nav">
            <span class="menuButton"><cl:homeLink/></span>
            <span class="menuButton"><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></span>
        </div>
        <div class="body">
            <h1><g:message code="default.list.label" args="[entityName]" /></h1>
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>
            <div class="list">
                <table>
                    <thead>
                        <tr>
                        
                            <g:sortableColumn property="id" title="${message(code: 'providerMap.id.label', default: 'Id')}" />
                        
                            <th><g:message code="providerMap.providerGroup.label" default="Provider Group" /></th>
                   	    
                            <g:sortableColumn property="exact" title="${message(code: 'providerMap.exact.label', default: 'Exact')}" />
                        
                            <g:sortableColumn property="matchAnyCollectionCode" title="${message(code: 'providerMap.matchAnyCollectionCode.label', default: 'Match Any Collection Code')}" />
                        
                            <th>Institution Codes</th>
                        
                            <th>Collection Codes</th>
                        
                        </tr>
                    </thead>
                    <tbody>
                    <g:each in="${providerMapInstanceList}" status="i" var="providerMapInstance">
                        <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                        
                            <td><g:link action="show" id="${providerMapInstance.id}">${fieldValue(bean: providerMapInstance, field: "id")}</g:link></td>
                        
                            <td>${fieldValue(bean: providerMapInstance, field: "providerGroup")}</td>
                        
                            <td><g:formatBoolean boolean="${providerMapInstance.exact}" /></td>
                        
                            <td><g:formatBoolean boolean="${providerMapInstance.matchAnyCollectionCode}" /></td>
                        
                            <td>${providerMapInstance.getInstitutionCodes().join(' ')}</td>
                        
                            <td>${providerMapInstance.getCollectionCodes().join(' ')}</td>
                        
                        </tr>
                    </g:each>
                    </tbody>
                </table>
            </div>
            <div class="paginateButtons">
                <g:paginate total="${providerMapInstanceTotal}" />
            </div>
        </div>
    </body>
</html>