<!-- Attributions -->
<div class="show-section">
  <h2>Attributions</h2>
  <ul class="fancy">
    <g:each in="${instance.getAttributionList()}" var="att">
      <li>${att.name}</li>
    </g:each>
  </ul>
  <div style="clear:both;"></div>
  <cl:editButton uid="${instance.uid}" action="editAttributions" target="${target}"/>
</div>
