Feature: Push Notifications

  Scenario: I can locally schedule a push notification
    When I press "Notify Me!"
      Then I insult Android
    When I press the home button
      Then I should be on the home screen
    When I click a notification with "Some text!"
      Then I should see "Teak Example"
