<#-- Choix de la methode de connexion. Dans Tabibi on n'y arrive QUE depuis le bouton « Continuer par e-mail »
     de l'ecran telephone : on saute donc automatiquement vers l'option e-mail (nom d'utilisateur + mot de passe)
     et on masque l'option telephone (SMS), pour ne pas re-proposer le SMS dans le parcours e-mail.
     Repli : si l'option e-mail est absente, on reaffiche toutes les options. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>
  <#if section = "header">
    <div class="kc-heading">
      <h1>${msg("auth.email.title")}</h1>
      <p>${msg("auth.email.subtitle")}</p>
    </div>
  <#elseif section = "form">
    <form class="kc-form" id="kc-select-credential-form" action="${url.loginAction}" method="post">
      <#list auth.authenticationSelections as authenticationSelection>
        <#assign isPhone = ((authenticationSelection.authenticationExecution.authenticator)!"") == "tabibi-phone-otp">
        <div class="n-field"<#if isPhone> hidden</#if>>
          <button class="n-btn n-btn--secondary n-btn--block tabibi-sel" data-phone="${isPhone?string('1','0')}"
                  type="submit" name="authenticationExecution" value="${authenticationSelection.authExecId}">
            ${msg('${authenticationSelection.displayName}')}
          </button>
          <#if (authenticationSelection.helpText)?has_content>
            <span class="kc-muted">${msg('${authenticationSelection.helpText}')}</span>
          </#if>
        </div>
      </#list>
    </form>
    <script>
      (function(){
        var mail = document.querySelector('.tabibi-sel[data-phone="0"]');
        if (mail) { mail.click(); }
        else { document.querySelectorAll('#kc-select-credential-form .n-field[hidden]').forEach(function(e){ e.hidden = false; }); }
      })();
    </script>
  </#if>
</@layout.registrationLayout>
