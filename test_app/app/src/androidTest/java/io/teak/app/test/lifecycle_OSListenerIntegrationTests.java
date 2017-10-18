/* Teak -- Copyright (C) 2017 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.teak.app.test;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.event.LifecycleEvent;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class lifecycle_OSListenerIntegrationTests extends TeakIntegrationTest {

    @Test
    public void basicLifecycle() {
        launchActivity();
    }

    @Test
    public void integratedLifecycle() {
        launchActivity();

        // Background the app, it should now be paused
        backgroundApp();
        verify(eventListener, timeout(5000).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        // Bring the app back, it should be resumed again
        foregroundApp();
        verify(eventListener, timeout(5000).times(2)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Resumed);
    }
}
