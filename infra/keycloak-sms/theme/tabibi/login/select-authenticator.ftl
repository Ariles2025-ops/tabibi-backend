<#-- Choix de la methode de connexion (Keycloak standard), mis a la charte Tabibi.
     Boucle sur auth.authenticationSelections ; chaque option soumet le formulaire
     avec authenticationExecution = authExecId, action ${url.loginAction}. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("loginChooseAuthenticator")}</h1>
    </div>
  <#elseif section = "form">
    <form class="kc-form" id="kc-select-credential-form" action="${url.loginAction}" method="post">
      <#list auth.authenticationSelections as authenticationSelection>
        <div class="n-field">
          <button class="n-btn n-btn--secondary n-btn--block" type="submit" name="authenticationExecution" value="${authenticationSelection.authExecId}">
            ${msg('${authenticationSelection.displayName}')}
          </button>
          <#if (authenticationSelection.helpText)?has_content>
            <span class="kc-muted">${msg('${authenticationSelection.helpText}')}</span>
          </#if>
        </div>
      </#list>
    </form>
  </#if>
</@layout.registrationLayout>
