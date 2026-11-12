<#-- Mot de passe oublie (Keycloak standard), mis a la charte Tabibi.
     Action ${url.loginAction}, champ 'username'. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("emailForgotTitle")}</h1>
    </div>
  <#elseif section = "form">
    <form class="kc-form" id="kc-reset-password-form" action="${url.loginAction}" method="post" novalidate>
      <label class="n-field">
        <span class="n-field__label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></span>
        <span class="n-input kc-ltr<#if messagesPerField.existsError('username')> n-input--error</#if>">
          <input class="kc-input" id="username" name="username" type="text" autofocus value="${(auth.attemptedUsername!'')}" autocomplete="username" dir="ltr"
                 aria-invalid="<#if messagesPerField.existsError('username')>true</#if>">
        </span>
        <#if messagesPerField.existsError('username')>
          <span class="n-field__error">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
        </#if>
      </label>
      <div class="kc-foot">
        <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit">${msg("doSubmit")}</button>
      </div>
      <p class="kc-muted"><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></p>
    </form>
  <#elseif section = "info">
    <p class="kc-muted">
      <#if realm.duplicateEmailsAllowed>
        ${msg("emailInstructionUsername")}
      <#else>
        ${msg("emailInstruction")}
      </#if>
    </p>
  </#if>
</@layout.registrationLayout>
