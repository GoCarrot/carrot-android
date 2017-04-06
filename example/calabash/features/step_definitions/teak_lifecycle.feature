Feature: Teak Lifecycle

  Scenario: Basic app lifecycle
    Given I see "Teak Example"
      Then the Teak state should be "Active"
    When I press the home button
      Then the Teak state should be "Paused"
      And the Teak Session state should be "Expiring"
    When I launch the app again
      Then the Teak state should be "Active"
      And I should see "Teak Example"
