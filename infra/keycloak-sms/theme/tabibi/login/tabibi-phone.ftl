<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
  <#-- On affiche d'abord le choix (telephone / e-mail). Si une erreur revient ou qu'un numero est deja saisi,
       on ouvre directement l'ecran du numero pour ne pas perdre le contexte. -->
  <#-- Seule une erreur/alerte ouvre l'ecran du numero : un message de succes ou d'info (ex. « vous allez recevoir
       un courriel » apres mot de passe oublie) garde l'ecran de choix, pour revenir naturellement vers l'e-mail. -->
  <#assign startPhone = (message?? && message.summary?has_content && message.type != 'success' && message.type != 'info') || ((phone)!'')?has_content>
  <#if section = "header">
  <#elseif section = "form">
    <div id="choice-screen"<#if startPhone> hidden</#if>>
      <div class="kc-heading">
        <h1>${msg("auth.choose.title")}</h1>
        <p>${msg("auth.choose.subtitle")}</p>
      </div>
      <div class="kc-choice">
        <button type="button" class="n-btn n-btn--primary n-btn--lg n-btn--block kc-choicebtn" onclick="tabibiShowPhone()">
          <span class="kc-choicebtn__ic" aria-hidden="true">&#128241;</span>
          <span class="kc-choicebtn__tx">${msg("auth.choose.phone")}<small>${msg("auth.choose.phoneHint")}</small></span>
        </button>
        <#if auth?? && auth.showTryAnotherWayLink()>
          <div class="kc-or">${msg("auth.choose.or")}</div>
          <button type="button" class="n-btn n-btn--lg n-btn--block kc-choicebtn kc-choicebtn--ghost" onclick="document.getElementById('kc-try-another').submit();return false;">
            <span class="kc-choicebtn__ic" aria-hidden="true">&#9993;</span>
            <span class="kc-choicebtn__tx">${msg("auth.choose.email")}<small>${msg("auth.choose.emailHint")}</small></span>
          </button>
        </#if>
      </div>
    </div>

    <div id="phone-screen"<#if !startPhone> hidden</#if>>
      <button type="button" class="kc-back" onclick="tabibiShowChoice()">${msg("auth.choose.back")}</button>
      <div class="kc-heading">
        <h1>${msg("auth.phone.title")}</h1>
        <p>${msg("auth.phone.subtitle")}</p>
      </div>
      <form class="kc-form" action="${url.loginAction}" method="post" novalidate>
        <label class="n-field">
          <span class="n-field__label">${msg("auth.phone.label")}</span>
          <span class="n-input kc-ltr">
            <span class="kc-prefix">+213</span><span class="kc-sep" aria-hidden="true"></span>
            <input class="kc-input" type="tel" name="phone" inputmode="numeric" autocomplete="tel-national"
                   placeholder="${msg("auth.phone.placeholder")}" value="${(phone!'')}" maxlength="14" required>
          </span>
        </label>
        <label class="kc-check">
          <input type="checkbox" name="consent" value="yes" <#if consent??>checked</#if> required>
          <span>${msg("auth.phone.consent")}</span>
        </label>
        <p class="kc-legal">
          <a href="${properties.tabibiSiteUrl}/legal/cgu.html<#if locale?? && locale.currentLanguageTag == "ar">#ar</#if>" target="_blank" rel="noopener">${msg("auth.legal.cgu")}</a>
          <a href="${properties.tabibiSiteUrl}/legal/confidentialite.html<#if locale?? && locale.currentLanguageTag == "ar">#ar</#if>" target="_blank" rel="noopener">${msg("auth.legal.privacy")}</a>
        </p>
        <div class="kc-foot">
          <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit">${msg("auth.phone.submit")}</button>
        </div>
      </form>
    </div>

    <#if auth?? && auth.showTryAnotherWayLink()>
      <form id="kc-try-another" action="${url.loginAction}" method="post">
        <input type="hidden" name="tryAnotherWay" value="on"/>
      </form>
    </#if>
    <script>
      function tabibiShowPhone(){document.getElementById('choice-screen').hidden=true;var p=document.getElementById('phone-screen');p.hidden=false;var i=p.querySelector('input[name=phone]');if(i){i.focus();}}
      function tabibiShowChoice(){document.getElementById('phone-screen').hidden=true;document.getElementById('choice-screen').hidden=false;}
    </script>
  </#if>
</@layout.registrationLayout>
