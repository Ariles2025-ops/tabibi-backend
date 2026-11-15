<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("auth.otp.title")}</h1>
      <p>${msg("auth.otp.subtitle", phoneDisplay)}</p>
    </div>
  <#elseif section = "form">
    <form class="kc-form" action="${url.loginAction}" method="post" novalidate>
      <label class="n-field">
        <span class="n-field__label">${msg("auth.otp.label")}</span>
        <span class="n-input kc-ltr">
          <input class="kc-input kc-code" type="text" name="code" inputmode="numeric" autocomplete="one-time-code"
                 pattern="[0-9]{6}" maxlength="6" autofocus required>
        </span>
      </label>
      <#-- Le bouton Valider est le premier du formulaire : la touche Entrée du clavier valide le code
           (sinon le navigateur enverrait « Changer de numéro »). -->
      <div class="kc-submit">
        <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit" name="action" value="verify">${msg("auth.otp.submit")}</button>
      </div>
      <div class="kc-links">
        <#if resendIn gt 0>
          <span class="kc-muted">${msg("auth.otp.resendIn", resendInText)}</span>
        <#else>
          <button class="n-btn n-btn--ghost" type="submit" name="action" value="resend" formnovalidate>${msg("auth.otp.resend")}</button>
        </#if>
        <button class="n-btn n-btn--ghost" type="submit" name="action" value="restart" formnovalidate>${msg("auth.otp.changePhone")}</button>
      </div>
    </form>
  </#if>
</@layout.registrationLayout>
