<?php
/**
 * A simple CSRF class to protect forms against CSRF attacks. The class uses
 * PHP sessions for storage.
 * 
 * @author Raahul Seshadri
 *
 */
class CSRF_Protect
{
	/**
	 * The namespace for the session variable and form inputs
	 * @var string
	 */
	private $namespace;
	
	/**
	 * Initializes the session variable name, starts the session if not already so,
	 * and initializes the token
	 * 
	 * @param string $namespace
	 */
	public function __construct($namespace = '_csrf')
	{
		$this->namespace = $namespace;
		
		if (session_id() === '')
		{
			session_start();
		}
		
		$this->setToken();
	}
	
	/**
	 * Return the token from persistent storage
	 * 
	 * @return string
	 */
	public function getToken()
	{
		return $this->readTokenFromStorage();
	}
	
	/**
	 * Verify if supplied token matches the stored token
	 * 
	 * @param string $userToken
	 * @return boolean
	 */
	public function isTokenValid($userToken)
	{
		return is_string($userToken) && $userToken !== '' && hash_equals($this->readTokenFromStorage(), $userToken);
	}
	
	/**
	 * Echoes the HTML input field with the token, and namespace as the
	 * name of the field
	 */
	public function echoInputField()
	{
		$token = $this->getToken();
		echo "<input type=\"hidden\" name=\"{$this->namespace}\" value=\"{$token}\" />";
	}
	
	/**
	 * Verifies whether the post token was set, else dies with error
	 */
	public function verifyRequest()
	{
		if (!$this->isTokenValid($_POST[$this->namespace] ?? ''))
		{
			die("CSRF validation failed.");
		}
	}

	/**
	 * Check if the CSRF token from POST request is valid
	 * Returns true/false instead of dying
	 * 
	 * @return boolean
	 */
	public function checkToken()
	{
		if (!isset($_POST[$this->namespace]))
		{
			return false;
		}
		return $this->isTokenValid($_POST[$this->namespace]);
	}
	
	/**
	 * Generates a new token value and stores it in persisent storage, or else
	 * does nothing if one already exists in persisent storage
	 */
	private function setToken()
	{
		$storedToken = $this->readTokenFromStorage();
		
		if ($storedToken === '')
		{
			$token = bin2hex(random_bytes(32));
			$this->writeTokenToStorage($token);
		}
	}
	
	/**
	 * Reads token from persistent sotrage
	 * @return string
	 */
	private function readTokenFromStorage()
	{
		if (isset($_SESSION[$this->namespace]))
		{
			return $_SESSION[$this->namespace];
		}
		else
		{
			return '';
		}
	}
	
	/**
	 * Writes token to persistent storage
	 */
	private function writeTokenToStorage($token)
	{
		$_SESSION[$this->namespace] = $token;
	}
}
