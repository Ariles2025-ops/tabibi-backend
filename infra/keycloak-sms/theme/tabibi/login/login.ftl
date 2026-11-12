<#-- Connexion par identifiant / mot de passe (Keycloak standard), mise a la charte Tabibi. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=(realm.password && realm.registrationAllowed && !registrationDisabled??); section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("loginAccountTitle")}</h1>
    </div>
  <#elseif section = "form">
    <#if realm.password>
      <form class="kc-form" id="kc-form-login" action="${url.loginAction}" method="post" onsubmit="login.disabled = true; return true;" novalidate>
        <#if !usernameHidden??>
          <label class="n-field">
            <span class="n-field__label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></span>
            <span class="n-input kc-ltr<#if messagesPerField.existsError('username','password')> n-input--error</#if>">
              <input class="kc-input" id="username" name="username" type="text" value="${(login.username!'')}" autofocus autocomplete="username" dir="ltr"
                     aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>">
            </span>
          </label>
        </#if>
        <label class="n-field">
          <span class="n-field__label">${msg("password")}</span>
          <span class="n-input<#if messagesPerField.existsError('username','password')> n-input--error</#if>">
            <input class="kc-input" id="password" name="password" type="password" autocomplete="current-password"
                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>">
          </span>
        </label>
        <#if messagesPerField.existsError('username','password')>
          <span class="n-field__error" id="input-error" aria-live="polite">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
        </#if>
        <#if (realm.rememberMe && !usernameHidden??) || realm.resetPasswordAllowed>
          <div class="kc-links">
            <#if realm.rememberMe && !usernameHidden??>
              <label class="kc-check">
                <input type="checkbox" id="rememberMe" name="rememberMe" <#if login.rememberMe??>checked</#if>>
                <span>${msg("rememberMe")}</span>
              </label>
            </#if>
            <#if realm.resetPasswordAllowed>
              <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
            </#if>
          </div>
        </#if>
        <div class="kc-foot">
          <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>
          <button class="n-btn n-btn--primary n-btn--lg n-btn--block" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
        </div>
      </form>
    </#if>
    <#if realm.password && social?? && social.providers?has_content>
      <hr class="n-divider" aria-hidden="true">
      <div class="kc-form" id="kc-social-providers">
        <#list social.providers as p>
          <a class="n-btn n-btn--secondary n-btn--block" id="social-${p.alias}" href="${p.loginUrl}">${p.displayName!}</a>
        </#list>
      </div>
    </#if>
  <#elseif section = "info">
    <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
      <p class="kc-muted" id="kc-registration">
        <span>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></span>
      </p>
    </#if>
  </#if>
</@layout.registrationLayout>
