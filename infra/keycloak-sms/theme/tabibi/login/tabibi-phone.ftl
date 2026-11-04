<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("auth.phone.title")}</h1>
      <p>${msg("auth.phone.subtitle")}</p>
    </div>
  <#elseif section = "form">
    <form class="kc-form" action="${url.loginAction}" method="post" novalidate>
      <label class="n-field">
        <span class="n-field__label">${msg("auth.phone.label")}</span>
        <span class="n-input kc-ltr">
          <span class="kc-prefix">+213</span><span class="kc-sep" aria-hidden="true"></span>
          <input class="kc-input" type="tel" name="phone" inputmode="numeric" autocomplete="tel-national" autofocus
                 placeholder="${msg("auth.phone.placeholder")}" value="${(phone!'')}" maxlength="14" required>
        </span>
      </label>
      <label class="kc-check">
        <input type="checkbox" name="consent" value="yes" <#if consent??>checked</#if> required>
        <span>${msg("auth.phone.consent")}</span>
      </label>
      <div class="kc-foot">
        <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit">${msg("auth.phone.submit")}</button>
      </div>
    </form>
  </#if>
</@layout.registrationLayout>
