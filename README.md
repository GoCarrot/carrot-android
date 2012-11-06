Required changes to AndroidManifest.xml:

Add to the `<application>` section:

	<service android:name="org.openudid.OpenUDID_service">
		<intent-filter>
			<action android:name="org.openudid.GETUDID" />
		</intent-filter>
	</service>
