Feature: Push Notifications

  Scenario: I can locally schedule a push notification
    When I press "Notify Me!"
    Then I insult Android
    When I click a notification with "Some text!"
    Then I should see "Teak Example"
