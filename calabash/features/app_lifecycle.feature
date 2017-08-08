Feature: App Lifecycle

  Scenario: Basic app lifecycle
    Given my app is in the foreground
      Then the Teak state should be "Active"
    When I press the home button
      Then I should be on the home screen
      And the Teak state should be "Paused"
    When I launch the app again
      Then my app should be in the foreground
      And the Teak state should be "Active"

  Scenario: Teak Session expiration
    Given my app is in the foreground
      Then I wait for the Teak Session state to be "UserIdentified"
    When I press the home button
      Then I should be on the home screen
      And the Teak Session state should be "Expiring"
    When I launch the app again
      Then I wait for the Teak Session state to be "UserIdentified"
      And the Teak Session state should have transitioned from "Expiring"
    Then I press the home button
      And I should be on the home screen
      And the Teak Session state should be "Expiring"
    Given the Teak Session timeout is 5 seconds
      And I wait for 5 seconds
    When I launch the app again
      Then my app should be in the foreground
      Then I wait for the Teak Session state to be "UserIdentified"
      And the Teak Session state should have transitioned from "IdentifyingUser"
