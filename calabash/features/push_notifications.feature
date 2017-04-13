Feature: Push Notifications

  Scenario: I can locally schedule a push notification
    Given I want a notification that says "Calabash Local Push" in 5 seconds
    When I press the home button
      Then I should be on the home screen
      Then I insult Android
      And I wait for 5 seconds
    When I click a notification with "Calabash Local Push"
      Then my app should be in the foreground
      And the current Teak session attribution should have "teak_notif_id"

  Scenario: I receive push notifications with a killed app
    Given I want a notification that says "Calabash Local Push" in 5 seconds
    When I press the home button
      Then I should be on the home screen
    Then the OS kills my app
      And I wait for 5 seconds
    When I click a notification with "Calabash Local Push"
      Then my app should be in the foreground
      And the current Teak session attribution should have "teak_notif_id"
