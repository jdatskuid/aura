    <!--

    Copyright (C) 2013 salesforce.com, inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<aura:application
	access="global"
    template="auradocs:template"
    controller="java://org.auraframework.docs.DocsController"
    useAppcache="false"
    locationChangeEvent="auradocs:locationChange"
    implements="auraStorage:refreshObserver">

    <aura:dependency resource="auraStorage:*" />

    <aura:attribute name="waitingCount" type="Integer" access="private" default="0"/>

    <aura:handler event="aura:waiting" action="{!c.waiting}"/>
    <aura:handler event="aura:doneWaiting" action="{!c.doneWaiting}"/>
    <aura:handler event="auradocs:locationChange" action="{!c.locationChange}"/>
    <aura:handler name="refreshBegin" action="{!c.refreshBegin}"/>
    <aura:handler name="refreshEnd" action="{!c.refreshEnd}"/>
		
    <header><auradocs:nav aura:id="navbar"/></header>

    <div class="container" aura:id="container">
        <aside aura:id="sidebar" class="sidebar"></aside>
        <article aura:id="content" class="content"></article>
        <footer>Copyright &copy; 2015 salesforce.com, inc.</footer>
    </div>
</aura:application>
