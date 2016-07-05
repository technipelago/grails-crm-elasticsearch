<%@ page import="grails.util.GrailsNameUtils" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <title><g:message code="elasticsearch.search.title" default="Search"/></title>
</head>

<body>

<crm:header title="elasticsearch.search.title" subtitle="elasticsearch.totalCount.label" args="${[params.q, totalCount]}"/>

<g:if test="${result}">
    <table class="table table-striped">
        <thead>
        <tr>
            <th><g:message code="elasticsearch.instance.label" default="Object"/></th>
            <th><g:message code="elasticsearch.type.label" default="Type"/></th>
        </tr>
        </thead>
        <tbody>
        <g:each in="${result}" var="m">
            <tr>
                <td><crm:referenceLink reference="${m}">${m}</crm:referenceLink></td>
                <td>${message(code: grails.util.GrailsNameUtils.getPropertyName(m.class) + '.label')}</td>
            </tr>
        </g:each>
        </tbody>
    </table>

    <crm:paginate total="${totalCount}" params="${[q:params.q]}"/>

</g:if>

</body>