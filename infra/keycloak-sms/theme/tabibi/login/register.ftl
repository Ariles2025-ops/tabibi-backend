<#-- Inscription (Keycloak standard), mise a la charte Tabibi.
     Champs et noms exacts attendus par Keycloak : firstName, lastName, email, username,
     password, password-confirm ; action ${url.registrationAction}. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true; section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("registerTitle")}</h1>
    </div>
  <#elseif section = "form">
    <form class="kc-form" id="kc-register-form" action="${url.registrationAction}" method="post" novalidate>
      <label class="n-field">
        <span class="n-field__label">${msg("firstName")}</span>
        <span class="n-input<#if messagesPerField.existsError('firstName')> n-input--error</#if>">
          <input class="kc-input" id="firstName" name="firstName" type="text" value="${(register.formData.firstName!'')}" autocomplete="given-name"
                 aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>">
        </span>
        <#if messagesPerField.existsError('firstName')>
          <span class="n-field__error">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
        </#if>
      </label>

      <label class="n-field">
        <span class="n-field__label">${msg("lastName")}</span>
        <span class="n-input<#if messagesPerField.existsError('lastName')> n-input--error</#if>">
          <input class="kc-input" id="lastName" name="lastName" type="text" value="${(register.formData.lastName!'')}" autocomplete="family-name"
                 aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>">
        </span>
        <#if messagesPerField.existsError('lastName')>
          <span class="n-field__error">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
        </#if>
      </label>

      <label class="n-field">
        <span class="n-field__label">${msg("email")}</span>
        <span class="n-input kc-ltr<#if messagesPerField.existsError('email')> n-input--error</#if>">
          <input class="kc-input" id="email" name="email" type="email" value="${(register.formData.email!'')}" autocomplete="email" dir="ltr"
                 aria-invalid="<#if messagesPerField.existsError('email')>true</#if>">
        </span>
        <#if messagesPerField.existsError('email')>
          <span class="n-field__error">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
        </#if>
      </label>

      <#if !realm.registrationEmailAsUsername>
        <label class="n-field">
          <span class="n-field__label">${msg("username")}</span>
          <span class="n-input kc-ltr<#if messagesPerField.existsError('username')> n-input--error</#if>">
            <input class="kc-input" id="username" name="username" type="text" value="${(register.formData.username!'')}" autocomplete="username" dir="ltr"
                   aria-invalid="<#if messagesPerField.existsError('username')>true</#if>">
          </span>
          <#if messagesPerField.existsError('username')>
            <span class="n-field__error">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
          </#if>
        </label>
      </#if>

      <#if passwordRequired??>
        <label class="n-field">
          <span class="n-field__label">${msg("password")}</span>
          <span class="n-input<#if messagesPerField.existsError('password','password-confirm')> n-input--error</#if>">
            <input class="kc-input" id="password" name="password" type="password" autocomplete="new-password"
                   aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>">
          </span>
          <#if messagesPerField.existsError('password')>
            <span class="n-field__error">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
          </#if>
        </label>

        <label class="n-field">
          <span class="n-field__label">${msg("passwordConfirm")}</span>
          <span class="n-input<#if messagesPerField.existsError('password-confirm')> n-input--error</#if>">
            <input class="kc-input" id="password-confirm" name="password-confirm" type="password" autocomplete="new-password"
                   aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>">
          </span>
          <#if messagesPerField.existsError('password-confirm')>
            <span class="n-field__error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
          </#if>
        </label>
      </#if>

      <#if termsAcceptanceRequired??>
        <label class="kc-check">
          <input type="checkbox" id="termsAccepted" name="termsAccepted" <#if register.formData.termsAccepted??>checked</#if>
                 aria-invalid="<#if messagesPerField.existsError('termsAccepted')>true</#if>">
          <span>${msg("termsText")}</span>
        </label>
        <#if messagesPerField.existsError('termsAccepted')>
          <span class="n-field__error">${kcSanitize(messagesPerField.get('termsAccepted'))?no_esc}</span>
        </#if>
      </#if>

      <#if recaptchaRequired?? && (recaptchaVisible!false)>
        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}" data-action="${recaptchaAction}"></div>
      </#if>

      <div class="kc-foot">
        <#if recaptchaRequired?? && !(recaptchaVisible!false)>
          <script src="https://www.google.com/recaptcha/api.js?render=${recaptchaSiteKey}"></script>
          <script>
            function onSubmitRecaptcha(token) { document.getElementById("kc-register-form").requestSubmit(); }
          </script>
          <button class="n-btn n-btn--primary n-btn--lg n-btn--block g-recaptcha" data-sitekey="${recaptchaSiteKey}" data-callback="onSubmitRecaptcha" data-action="${recaptchaAction}" type="submit">${msg("doRegister")}</button>
        <#else>
          <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit">${msg("doRegister")}</button>
        </#if>
      </div>

      <p class="kc-muted"><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></p>
    </form>
  </#if>
</@layout.registrationLayout>
